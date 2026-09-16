package com.pubpeb.compass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.pubpeb.compass.ui.theme.appColors

private val PILL_SHAPE = RoundedCornerShape(percent = 50)

/**
 * The north reference, as a two-position control.
 *
 * ### Why both segments are always drawn
 * This replaced a bare `MAG` label that became `TRUE` when toggled — three
 * letters at the same size, weight and colour as the non-interactive text
 * beside them, in a row that could hold two invisible tap targets and give no
 * clue which was which. A fresh user had no way to learn that a second mode
 * existed at all, which in practice is close to it not existing.
 *
 * Keeping the inactive segment permanently on screen **is** the discovery
 * mechanism. A dim `TRUE` says "there is another mode and you are not in it"
 * to somebody who has never heard the word declination — no onboarding
 * overlay, no coach mark, no first-run tooltip.
 *
 * ### Why the active fill is not green
 * `levelOk` means "the sensor reading can be trusted". True north being
 * selected while accuracy is poor is a real, simultaneous state, so painting
 * both in the same green would assert a relationship that does not exist. The
 * active segment uses a tint of `textPrimary` instead.
 *
 * ### Why [awaitingFix] is spoken and never drawn
 * It used to add an animated ellipsis inside the TRUE segment. Two things were
 * wrong with that. It never stopped — the state behind it was "no declination
 * yet", which a failed fetch satisfies forever — and even bounded it put a
 * looping animation inside a two-state control on the app's main screen, where
 * the only moving thing is supposed to be the rose. Waiting and failing are
 * both reported once, in words, on the note line below the pill. Here
 * [awaitingFix] only changes what a screen reader says.
 */
@Composable
fun NorthModePill(
    trueActive: Boolean,
    availability: NorthModeAvailability,
    awaitingFix: Boolean,
    onSelectMagnetic: () -> Unit,
    onSelectTrue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = appColors
    val blocked = availability == NorthModeAvailability.BLOCKED

    Row(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clip(PILL_SHAPE)
            .background(c.chipBg, PILL_SHAPE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Segment(
            label = "MAG",
            active = !trueActive,
            dimmed = false,
            leadingLock = false,
            onClick = onSelectMagnetic,
            description = if (trueActive) {
                "True north selected. Double tap to switch to magnetic north."
            } else {
                "Magnetic north selected."
            },
        )
        Segment(
            label = "TRUE",
            active = trueActive,
            // Blocked dims further than merely-inactive, so "not selected" and
            // "cannot be selected" are distinguishable from each other and not
            // only from selected.
            dimmed = blocked,
            leadingLock = blocked,
            onClick = onSelectTrue,
            description = when {
                blocked -> "True north unavailable, location access is off. Double tap for help."
                trueActive -> "True north selected."
                awaitingFix -> "Getting a location fix for true north."
                else -> "Magnetic north selected. Double tap to switch to true north."
            },
        )
    }
}

@Composable
private fun Segment(
    label: String,
    active: Boolean,
    dimmed: Boolean,
    leadingLock: Boolean,
    onClick: () -> Unit,
    description: String,
) {
    val c = appColors
    val fill = if (active) c.textPrimary.copy(alpha = 0.10f) else Color.Transparent
    val tint = when {
        active -> c.textPrimary
        dimmed -> c.textSecondary.copy(alpha = 0.5f)
        else -> c.textSecondary
    }

    Row(
        modifier = Modifier
            .clip(PILL_SHAPE)
            .background(fill, PILL_SHAPE)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingLock) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                // Decorative: the segment's own description already says it.
                // Left announceable, TalkBack reads "lock, true, unavailable"
                // as three separate things.
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(11.dp),
            )
            Spacer4()
        }
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
