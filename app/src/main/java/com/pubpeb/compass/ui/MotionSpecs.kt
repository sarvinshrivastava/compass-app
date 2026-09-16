package com.pubpeb.compass.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * The app's motion vocabulary, and the single place reduce-motion is honoured.
 *
 * ### Why this exists
 * `rememberReduceMotion()` was read at four of the twelve animating sites. The
 * eight that ignored it included every label on the AR overlay — so a user who
 * had turned animations off got a rose that snapped and a bubble that snapped,
 * while eight chips spring-glided over a full-screen live camera feed. The most
 * vestibularly aggressive motion in the app was the only motion exempt from the
 * setting, which is exactly backwards.
 *
 * Spec values were also ad hoc: three different stiffnesses and eight distinct
 * tween durations, chosen per call site.
 *
 * Every animation resolves its spec through one of these. A composable that
 * writes `spring(...)` or `tween(durationMillis = <literal>)` inline is a
 * defect — see AD-13 in the architecture spine.
 */

/** The compass card. Heavier than an instrument: it carries the most mass. */
@Composable
fun roseSpec(): FiniteAnimationSpec<Float> = motion(
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
)

/**
 * Bubble, plumb rule, AR labels and the AR lock marker.
 *
 * Critically damped, never bouncy: an instrument that overshoots crosses its
 * target twice and settles on neither pass, which for a level means it reports
 * "level" on the way past.
 */
@Composable
fun instrumentSpec(): FiniteAnimationSpec<Float> = motion(
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
)

/**
 * The AR horizon. Softest of the three, because it eases between *committed*
 * positions rather than tracking a live signal — see `HorizonCommitGate`.
 */
@Composable
fun horizonSpec(): FiniteAnimationSpec<Float> = motion(
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
)

/**
 * Fades, crossfades and tints. [durationMillis] comes from `Constants.Ui`.
 *
 * Generic in the animated type so one helper serves `animateFloatAsState`,
 * `animateColorAsState` and `Crossfade` alike — a colour tween that skipped the
 * reduce-motion gate because it was the wrong type would be exactly the kind of
 * hole this file exists to close.
 */
@Composable
fun <T> chromeSpec(durationMillis: Int): FiniteAnimationSpec<T> =
    if (rememberReduceMotion()) snap() else tween(durationMillis)

/**
 * The one gate. Anything a user has asked not to move, does not move — it
 * arrives at its destination in a single frame instead.
 */
@Composable
private fun motion(spec: FiniteAnimationSpec<Float>): FiniteAnimationSpec<Float> =
    if (rememberReduceMotion()) snap() else spec
