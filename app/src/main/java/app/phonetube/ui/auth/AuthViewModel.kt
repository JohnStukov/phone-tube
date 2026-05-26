package app.phonetube.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SignInUiState(
    val isLoading: Boolean = false,
    val userCode: String? = null,
    val verificationUrl: String? = null,
    val error: String? = null,
    val completed: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository.get(application)

    val authState: StateFlow<AuthState> = authRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), authRepository.state.value)

    private val _signInState = kotlinx.coroutines.flow.MutableStateFlow(SignInUiState())
    val signInState: StateFlow<SignInUiState> = _signInState.asStateFlow()

    fun startSignIn() {
        if (_signInState.value.isLoading) return
        viewModelScope.launch {
            _signInState.value = SignInUiState(isLoading = true)
            try {
                authRepository.signIn { code, url ->
                    _signInState.value = _signInState.value.copy(
                        userCode = code,
                        verificationUrl = url,
                        isLoading = true
                    )
                }
                withContext(Dispatchers.Main.immediate) {
                    _signInState.value = SignInUiState(completed = true)
                }
            } catch (e: Exception) {
                _signInState.value = SignInUiState(
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    fun selectAccount(accountId: Int) {
        authRepository.selectAccount(accountId)
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun resetSignInState() {
        _signInState.value = SignInUiState()
    }
}
