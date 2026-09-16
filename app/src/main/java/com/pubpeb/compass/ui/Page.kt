package com.pubpeb.compass.ui

/**
 * The pager's destinations, and the per-page behaviour that used to be decided
 * by integer index in three separate places.
 *
 * ### Why an enum and not an Int
 * `MainScreen` branched on the index three times: `settledPage == 0` held
 * `FLAG_KEEP_SCREEN_ON`, `currentPage == 1` fired the level-opened event, and
 * the pager's `when` built the screen. Only the last of those is obvious to
 * someone adding a page — so a third page would have silently lost
 * keep-screen-on, and inserting one at index 1 would have re-pointed the
 * analytics counter at the new screen while the once-per-session guard made the
 * mis-attribution look like a normal number.
 *
 * Declaring the behaviour on the entry means a new page has to answer for it,
 * and the exhaustive `when` in `MainScreen` fails to compile until it does.
 */
enum class Page(
    /**
     * Whether the display is held awake while this page is settled.
     *
     * True for pages someone reads without touching — a bearing is taken at
     * arm's length over seconds. False elsewhere, because the flag is only
     * released when it stops being true.
     */
    val keepScreenOn: Boolean,
    /**
     * Whether this page pins the device to portrait.
     *
     * The compass does: its rose is a circle capped on width, under a 52 sp
     * readout, and landscape takes away the one dimension the layout is built
     * around without revealing anything extra.
     *
     * The level does not. It measures how the device is held, so rotating the
     * device is a thing the user is *doing to the instrument*, and freezing the
     * UI while they do it reads as the screen having stopped responding.
     */
    val lockPortrait: Boolean,
) {
    COMPASS(keepScreenOn = true, lockPortrait = true),
    LEVEL(keepScreenOn = false, lockPortrait = false),
    ;

    companion object {
        val COUNT = entries.size

        /** The page at [index], or [COMPASS] if the pager reports one we lack. */
        fun at(index: Int): Page = entries.getOrElse(index) { COMPASS }
    }
}
