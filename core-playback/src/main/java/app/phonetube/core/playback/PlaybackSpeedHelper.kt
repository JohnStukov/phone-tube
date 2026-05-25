package app.phonetube.core.playback

object PlaybackSpeedHelper {
    private val SPEED_OPTIONS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    fun speedOptions(): List<Float> = SPEED_OPTIONS.toList()

    fun formatSpeedLabel(speed: Float): String {
        return if (speed == 1f) {
            "1x"
        } else {
            val text = speed.toString().trimEnd('0').trimEnd('.')
            "${text}x"
        }
    }
}
