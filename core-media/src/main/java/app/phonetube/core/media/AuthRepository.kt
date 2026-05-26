package app.phonetube.core.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.liskovsoft.mediaserviceinterfaces.oauth.Account
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.youtubeapi.service.YouTubeSignInService
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AuthState(
    val isSignedIn: Boolean = false,
    val selectedAccount: AccountInfo? = null,
    val accounts: List<AccountInfo> = emptyList()
)

class AuthRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val signInService: YouTubeSignInService = YouTubeSignInService.instance()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val accountListener = com.liskovsoft.mediaserviceinterfaces.SignInService.OnAccountChange {
        refreshState()
        YouTubeServiceManager.instance().refreshCacheIfNeeded()
    }

    init {
        PhoneTubeMediaInit.init(appContext)
        signInService.addOnAccountChange(accountListener)
        refreshState()
    }

    fun refreshState() {
        _state.value = readState()
    }

    suspend fun signIn(onUserCode: (userCode: String, verificationUrl: String) -> Unit) {
        withContext(Dispatchers.IO) {
            PhoneTubeMediaInit.init(appContext)
            suspendCancellableCoroutine { cont ->
                val disposable: Disposable = signInService.signInObserve()
                    // signInObserve() ends on mainThread; checkAuth() does sync HTTP and crashes on release.
                    .observeOn(Schedulers.io())
                    .subscribe(
                        { code ->
                            mainHandler.post {
                                onUserCode(code, DEFAULT_VERIFICATION_URL)
                            }
                        },
                        { error ->
                            if (cont.isActive) {
                                cont.resumeWithException(error)
                            }
                        },
                        {
                            refreshState()
                            if (cont.isActive) {
                                cont.resume(Unit)
                            }
                        }
                    )
                cont.invokeOnCancellation { disposable.dispose() }
            }
        }
    }

    fun selectAccount(accountId: Int) {
        val account = signInService.accounts?.firstOrNull { it.id == accountId } ?: return
        signInService.selectAccount(account)
        signInService.invalidateCache()
        signInService.checkAuth()
        refreshState()
        YouTubeServiceManager.instance().refreshCacheIfNeeded()
    }

    fun signOut() {
        val accounts = signInService.accounts?.toList().orEmpty()
        for (account in accounts) {
            signInService.removeAccount(account)
        }
        signInService.invalidateCache()
        refreshState()
        YouTubeServiceManager.instance().refreshCacheIfNeeded()
    }

    fun isSignedIn(): Boolean = signInService.isSigned

    private fun readState(): AuthState {
        val accounts = signInService.accounts?.map { it.toAccountInfo() }.orEmpty()
        val selected = signInService.selectedAccount?.toAccountInfo()
        return AuthState(
            isSignedIn = signInService.isSigned,
            selectedAccount = selected,
            accounts = accounts
        )
    }

    private fun Account.toAccountInfo() = AccountInfo(
        id = id,
        name = name,
        email = email,
        avatarUrl = avatarImageUrl,
        isSelected = isSelected
    )

    companion object {
        private const val DEFAULT_VERIFICATION_URL = "https://www.google.com/device"

        @Volatile
        private var instance: AuthRepository? = null

        fun get(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
