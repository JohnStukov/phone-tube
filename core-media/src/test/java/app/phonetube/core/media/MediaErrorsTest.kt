package app.phonetube.core.media

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MediaErrorsTest {

    @Test
    fun codeFor_networkErrors() {
        assertEquals(MediaErrors.NETWORK, MediaErrors.codeFor(UnknownHostException()))
        assertEquals(MediaErrors.NETWORK, MediaErrors.codeFor(SocketTimeoutException()))
        assertEquals(MediaErrors.NETWORK, MediaErrors.codeFor(IOException()))
    }

    @Test
    fun codeFor_signIn() {
        assertEquals(MediaErrors.SIGN_IN, MediaErrors.codeFor(NotSignedInException()))
    }

    @Test
    fun codeFor_genericFallback() {
        assertEquals(MediaErrors.GENERIC, MediaErrors.codeFor(IllegalStateException("boom")))
    }
}
