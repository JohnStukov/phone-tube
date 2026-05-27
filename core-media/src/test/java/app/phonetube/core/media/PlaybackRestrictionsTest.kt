package app.phonetube.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRestrictionsTest {
    @Test
    fun `live playback is detected for live and broadcast flags`() {
        assertTrue(PlaybackRestrictions.isLivePlayback(true, false))
        assertTrue(PlaybackRestrictions.isLivePlayback(false, true))
        assertFalse(PlaybackRestrictions.isLivePlayback(false, false))
    }

    @Test
    fun `playback is never blocked`() {
        assertFalse(PlaybackRestrictions.blocksPlayback(isLive = true, isLiveContent = true))
    }
}
