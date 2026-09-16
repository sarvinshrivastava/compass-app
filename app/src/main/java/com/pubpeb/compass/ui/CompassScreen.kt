package com.pubpeb.compass.ui

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pubpeb.compass.Constants
import com.pubpeb.compass.sensor.CompassAccuracy
import com.pubpeb.compass.ui.theme.AppPalette
import com.pubpeb.compass.ui.theme.ThemeMode
import com.pubpeb.compass.ui.theme.appColors
import com.pubpeb.compass.util.PermissionAskLog
import com.pubpeb.compass.util.bearingToFullLabel
import com.pubpeb.compass.util.bearingToSpokenLabel
import com.pubpeb.compass.util.normalizedHeading
import com.pubpeb.compass.util.openAppSettings
import com.pubpeb.compass.util.shortestArcDelta
import com.pubpeb.compass.viewmodel.CompassViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Root compass screen.
 *
 * **The compass is never gated on a permission.** A magnetometer heading needs
 * no consent to read, so [CompassContent] renders on the first frame; location
 * is requested only when the user taps the MAG tag, and only ever *upgrades*
 * the reading from magnetic to true north.
 *
 * [Manifest.permission.ACCESS_COARSE_LOCATION] is deliberate: declination
 * varies by roughly a degree over tens of kilometres, so a coarse fix is
 * indistinguishable from a fine one here — and reads far less alarming in the
 * system dialog, which is the difference between a grant and a denial.
 *
 * [isArMode] and [onArModeChange] are hoisted to [com.pubpeb.compass.ui.MainScreen]
 * so the pager can disable horizontal swipe while the camera overlay is active.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel = viewModel(),
    isArMode: Boolean = false,
    arWarmAllowed: Boolean = false,
    onArModeChange: (Boolean) -> Unit = {},
) {
    val c = appColors
    val context = LocalContext.current
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationGranted = locationPermission.status.isGranted

    // Observable shadow of the persisted "we have asked" marker; the stored
    // value survives process death, this makes a change to it recompose.
    var hasAsked by remember {
        mutableStateOf(
            PermissionAskLog.hasAsked(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // Denied twice is denied for good, and the platform reports that state as
    // "not granted, no rationale" — the same pair it reports before the first
    // ask. Without the marker the two are indistinguishable, and tapping the
    // MAG tag called launchPermissionRequest() into a system that silently
    // refuses it: the tag never flipped to TRUE, nothing explained why, and the
    // user concluded true north was broken.
    val locationBlocked = !locationGranted &&
        !locationPermission.status.shouldShowRationale &&
        hasAsked

    // Only ever fires to fetch declination — never to unblock the UI.
    LaunchedEffect(locationGranted) {
        if (locationGranted) viewModel.updateDeclination()
    }

    // Did asking for COARSE rather than FINE actually pay off in grant rate?
    // Reported once the user has been asked, not on the first composition.
    //
    // Keyed on hasAsked, not on shouldShowRationale: a permanent denial clears
    // the rationale flag, so the old condition dropped exactly the outcome that
    // most needs counting and biased the grant rate upward.
    LaunchedEffect(locationPermission.status, hasAsked) {
        if (hasAsked || locationGranted) {
            viewModel.onLocationOutcome(locationGranted)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = c.bgDeep,
    ) {
        CompassContent(
            viewModel         = viewModel,
            isArMode          = isArMode,
            arWarmAllowed     = arWarmAllowed,
            onArModeChange    = onArModeChange,
            locationGranted   = locationGranted,
            onRequestLocation = {
                if (locationBlocked) {
                    // The in-app prompt is spent. System settings is the only
                    // remaining route to true north, so send the user there
                    // rather than firing a request that cannot succeed.
                    context.openAppSettings()
                } else {
                    PermissionAskLog.recordAsked(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                    hasAsked = true
                    locationPermission.launchPermissionRequest()
                }
            },
        )
    }
}

// ── Main compass UI ───────────────────────────────────────────────────────────

@Composable
private fun CompassContent(
    viewModel: CompassViewModel,
    isArMode: Boolean,
    arWarmAllowed: Boolean,
    onArModeChange: (Boolean) -> Unit,
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
) {
    val azimuth        by viewModel.azimuth.collectAsStateWithLifecycle()
    val arAzimuth      by viewModel.arAzimuth.collectAsStateWithLifecycle()
    val horizonElev    by viewModel.arHorizonElevation.collectAsStateWithLifecycle()
    val trueNorth      by viewModel.trueNorth.collectAsStateWithLifecycle()
    val declination    by viewModel.declination.collectAsStateWithLifecycle()
    val hasDeclination by viewModel.hasDeclination.collectAsStateWithLifecycle()
    val accuracy       by viewModel.accuracy.collectAsStateWithLifecycle()
    val themeMode      by viewModel.themeMode.collectAsStateWithLifecycle()
    val arAutoSwitch   by viewModel.arAutoSwitch.collectAsStateWithLifecycle()
    val hasMagnetometer by viewModel.hasMagnetometer.collectAsStateWithLifecycle()
    val lockedBearing  by viewModel.lockedBearing.collectAsStateWithLifecycle()

    // ── Open the camera during the AR dwell, not after it ────────────────────
    // Measured on a Pixel 9a, the AR entry cost breaks down as:
    //
    //     provider resolved   + 78 ms
    //     bound               + 96 ms
    //     first frame         +507 ms   <- this is the delay
    //
    // An earlier version warmed only `ProcessCameraProvider.getInstance()`,
    // which is the 78 ms stage, specifically to avoid opening the camera early.
    // It was measurably almost useless: the cost is the sensor producing its
    // first frame, and nothing but actually opening the camera starts that
    // clock. So the camera is now bound during the dwell.
    //
    // The tradeoff, taken deliberately: the platform's camera indicator lights
    // up while the compass is still on screen, a few hundred milliseconds
    // before the AR view appears. That is a real cost for an app that makes a
    // point of not being creepy, and it is only spent when the user has already
    // raised the phone past the AR threshold — i.e. when they are on their way
    // into the camera view regardless.
    // Debounced in the ViewModel by CameraWarmGate, not here. `arArming` flips
    // at the sensor rate for a hand hovering at the threshold, and wiring that
    // straight to a camera bind strobes the hardware and the privacy
    // indicator. This composable only reads the decision.
    // Scoped by the caller: CompassScreen composes during a swipe from the
    // level page, and cameraWarm is derived from tilt alone. Left ungated, the
    // camera bound (and the privacy indicator lit) while the flat compass was
    // on screen — the same stale-intent-across-a-page-change the AR arming
    // guard exists to stop, one layer further down.
    val warmingCamera = arWarmAllowed &&
        viewModel.cameraWarm.collectAsStateWithLifecycle().value

    // Sits outside the Crossfade: a Dialog is an overlay and must not be torn
    // down and rebuilt when the AR/flat transition runs.
    var showSettings by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }

    // ── Automatic calibration prompt ─────────────────────────────────────────
    // At most once per session, and only after the heading has been untrustworthy
    // long enough to mean it. Saveable, so a rotation does not re-arm it.
    var autoPromptSpent by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(accuracy, autoPromptSpent) {
        if (autoPromptSpent || accuracy != CompassAccuracy.UNRELIABLE) return@LaunchedEffect
        // A change in accuracy cancels and relaunches this effect, so reaching
        // the far side of the delay *is* the proof that it stayed UNRELIABLE
        // throughout — no re-check needed here.
        delay(Constants.Ui.CALIBRATION_PROMPT_DELAY_MS)
        autoPromptSpent = true
        showCalibration = true
    }

    if (showSettings) {
        SettingsDialog(
            arAutoSwitch        = arAutoSwitch,
            onArAutoSwitchChange = viewModel::setArAutoSwitch,
            onOpenCalibration   = {
                showSettings = false
                showCalibration = true
            },
            onDismiss           = { showSettings = false },
        )
    }

    if (showCalibration) {
        CalibrationDialog(
            accuracy  = accuracy,
            onDismiss = { showCalibration = false },
        )
    }

    // Tapping the mode tag means "give me true north". If we cannot yet, that
    // means asking for location first — the toggle then applies on the next tap,
    // once a fix has produced a real declination.
    val onModeTagClick: () -> Unit = {
        if (locationGranted) viewModel.toggleTrueNorth() else onRequestLocation()
    }

    // ── The transition waits for a picture ───────────────────────────────────
    // Crossfading the instant the gate opens showed a black rectangle for half
    // a second while the sensor woke up, which reads as the app hanging. The
    // compass now holds until the preview is actually streaming, so AR arrives
    // fully formed. Combined with binding during the dwell, the wait usually
    // happens before the gate even opens and this costs nothing.
    var arStreaming by remember { mutableStateOf(false) }
    LaunchedEffect(isArMode, warmingCamera) {
        if (!isArMode && !warmingCamera) arStreaming = false
    }

    val arVisible = isArMode && arStreaming
    val arAlpha by animateFloatAsState(
        targetValue = if (arVisible) 1f else 0f,
        animationSpec = chromeSpec(Constants.Ui.MODE_TRANSITION_MS),
        label = "arFade",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Composed while arming too, invisibly — that is what gets the camera
        // open ahead of the gate. Kept out of the semantics tree while hidden
        // so a screen reader never lands on an overlay the user cannot see.
        if (isArMode || warmingCamera) {
            ArOverlayScreen(
                // Deadbanded heading for the numeric HUD, so the AR readout never
                // disagrees with the flat screen's readout for the same bearing.
                azimuth            = azimuth,
                // Heavy-filtered (α=0.05) — label positions only, where the extra
                // smoothing is worth the lag.
                arAzimuth          = arAzimuth,
                horizonElevationDeg = horizonElev,
                lockedBearing      = lockedBearing,
                onToggleLock       = viewModel::toggleBearingLock,
                onExitAr           = { onArModeChange(false) },
                onStreamingChanged = { arStreaming = it },
                modifier           = Modifier
                    .fillMaxSize()
                    .alpha(arAlpha)
                    .then(if (arVisible) Modifier else Modifier.clearAndSetSemantics {}),
            )
        }

        if (arAlpha < 1f) {
            CompassFlatContent(
                azimuth           = azimuth,
                trueNorth         = trueNorth,
                hasDeclination    = hasDeclination,
                declination       = declination,
                accuracy          = accuracy,
                themeMode         = themeMode,
                hasMagnetometer   = hasMagnetometer,
                lockedBearing     = lockedBearing,
                onModeTagClick    = onModeTagClick,
                onToggleLock      = viewModel::toggleBearingLock,
                onToggleDaylight  = viewModel::toggleDaylightMode,
                onOpenSettings    = { showSettings = true },
                onOpenCalibration = { showCalibration = true },
                onEnterAr         = { onArModeChange(true) },
                modifier          = Modifier.alpha(1f - arAlpha),
            )
        }
    }
}

// ── Flat compass layout (rose + readout + top bar) ────────────────────────────

@Composable
private fun CompassFlatContent(
    modifier: Modifier = Modifier,
    azimuth: Float,
    trueNorth: Boolean,
    hasDeclination: Boolean,
    declination: Float,
    accuracy: CompassAccuracy,
    themeMode: ThemeMode,
    hasMagnetometer: Boolean,
    lockedBearing: Float?,
    onModeTagClick: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleDaylight: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalibration: () -> Unit,
    onEnterAr: () -> Unit,
) {
    val c = appColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bgDeep)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Top bar: camera + daylight toggle (right-aligned) ─────────────
        TopInfoBar(
            themeMode        = themeMode,
            onToggleDaylight = onToggleDaylight,
            onOpenSettings   = onOpenSettings,
            onEnterAr        = onEnterAr,
        )

        // ── No magnetometer: say so, and stop ─────────────────────────────
        // Some phones ship without one. The rose and the readout render
        // perfectly on such a device and report a confident 0° NORTH forever,
        // because no sensor event ever arrives to move them — the one way to
        // reach a frozen heading that the staleness watchdog cannot see, since
        // it watches for emissions *stopping* and here they never start.
        if (!hasMagnetometer) {
            MissingSensorNotice(
                headline = "No compass sensor",
                detail   = "This device has no magnetometer, so it cannot measure " +
                    "a heading. The bubble level on the next page still works.",
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            return@Column
        }

        // ── Heading readout: the screen's hero, just above the rose ───────
        HeadingReadout(
            azimuth           = azimuth,
            trueNorth         = trueNorth,
            hasDeclination    = hasDeclination,
            declination       = declination,
            accuracy          = accuracy,
            lockedBearing     = lockedBearing,
            onModeTagClick    = onModeTagClick,
            onOpenCalibration = onOpenCalibration,
        )

        // ── Compass rose ──────────────────────────────────────────────────
        // Capped so the rose supports the readout rather than
        // dominating it, and `weight(fill = false)` bounds its height so a
        // short or landscape viewport shrinks it instead of overflowing.
        Box(
            modifier           = Modifier.weight(1f, fill = false),
            contentAlignment   = Alignment.Center,
        ) {
            CompassRose(
                azimuth  = azimuth,
                lockedBearing = lockedBearing,
                onToggleLock  = onToggleLock,
                modifier = Modifier
                    .sizeIn(maxWidth = Constants.Ui.ROSE_MAX_WIDTH)
                    .padding(8.dp),
            )
        }

        // Bottom spacer keeps the rose vertically balanced
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Top info bar ──────────────────────────────────────────────────────────────

@Composable
private fun TopInfoBar(
    themeMode: ThemeMode,
    onToggleDaylight: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterAr: () -> Unit,
) {
    val c = appColors
    val daylightOn = themeMode == ThemeMode.DAYLIGHT

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Camera icon — manually enter AR overlay mode
        IconButton(onClick = onEnterAr) {
            Icon(
                imageVector        = Icons.Filled.CameraAlt,
                contentDescription = "AR overlay",
                tint               = c.textSecondary,
            )
        }
        // Daylight mode — the outdoor high-contrast palette. Deliberately one
        // tap away, not buried in settings: it is needed exactly when the
        // screen is hardest to read.
        IconButton(onClick = onToggleDaylight) {
            Icon(
                imageVector        = if (daylightOn) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                contentDescription = if (daylightOn) "Leave daylight mode" else "Daylight mode",
                tint               = c.textSecondary,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector        = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint               = c.textSecondary,
            )
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────

/**
 * One switch, and one action.
 *
 * Everything else the user can change is reachable in one tap on the compass
 * itself: true north from the MAG/TRUE tag, daylight from the top bar. Only the
 * AR auto-switch belongs here, because it is a preference about behaviour
 * rather than a mode you flip while looking at a bearing.
 *
 * Calibration is the one action, and it is here because the automatic prompt is
 * the *platform's* opinion about whether the heading is trustworthy. Someone
 * who thinks the bearing looks wrong while the platform still reports it fine
 * needs a way to act on that without waiting for the platform to agree — and
 * "where is the calibration option" is a question a settings screen is expected
 * to answer.
 */
@Composable
private fun SettingsDialog(
    arAutoSwitch: Boolean,
    onArAutoSwitchChange: (Boolean) -> Unit,
    onOpenCalibration: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = appColors

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = c.bgCard) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "SETTINGS",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-switch to AR",
                            color = c.textPrimary,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = "Raise the phone to open the camera view",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Switch(
                        checked = arAutoSwitch,
                        onCheckedChange = onArAutoSwitchChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = c.levelOk,
                            checkedTrackColor   = c.levelOk.copy(alpha = 0.35f),
                            uncheckedThumbColor = c.textSecondary,
                            uncheckedTrackColor = c.buttonBg,
                        ),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clickable(onClick = onOpenCalibration)
                        .semantics { role = Role.Button },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Calibrate the compass",
                        color = c.textPrimary,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "What to do when the heading looks wrong",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Height held for the turn-guidance line whether or not it has anything to say.
 * 13 sp of monospace plus a little breathing room; see the note at its use site.
 */
private val LOCK_LINE_HEIGHT = 20.dp

// ── Heading readout (above rose) ──────────────────────────────────────────────

/**
 * Degree · cardinal · mode-and-confidence — in descending order of how often
 * someone needs it.
 *
 * The third line carries the whole trust story. It stays silent while the
 * magnetometer is fine and speaks only when it is not, in place, at the size
 * it already occupies: no dialog, banner or snackbar, none of which a person
 * holding a phone at arm's length in the field will read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeadingReadout(
    azimuth: Float,
    trueNorth: Boolean,
    hasDeclination: Boolean,
    declination: Float,
    accuracy: CompassAccuracy,
    lockedBearing: Float?,
    onModeTagClick: () -> Unit,
    onOpenCalibration: () -> Unit,
) {
    val c = appColors

    // 359.7° rounds to 360, which is not a heading anyone reads — fold it to 0.
    val rounded    = normalizedHeading(azimuth)
    val fullLabel  = bearingToFullLabel(azimuth)
    val trueActive = trueNorth && hasDeclination
    val confidence = confidenceNote(accuracy, c)

    // Read when the user focuses the readout, so asking for the heading always
    // returns the exact one. Rebuilt only when the whole degree changes; keyed
    // on the raw azimuth this would be rebuilt on every sensor event.
    val description = remember(rounded) {
        "Heading $rounded degrees, ${bearingToSpokenLabel(rounded.toFloat())}"
    }

    // ── What gets announced automatically, and how often ──────────────────────
    // Not the same string. A live region re-announces whenever its description
    // changes, and the precise one changes on every whole degree — roughly 360
    // announcements per rotation of the phone, which is not a heading readout
    // so much as a denial of service, and is exactly what the accessibility
    // guidance on live regions warns against.
    //
    // The useful hands-free signal is the sector, which changes eight times per
    // rotation. It rides on its own zero-size node so the visible readout can
    // keep its exact description for on-demand reads: one node cannot carry
    // both a precise description and a coarse announcement.
    val sector = remember(rounded) { bearingToSpokenLabel(rounded.toFloat()) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // ── Two nodes, deliberately unmerged ──────────────────────────────
        // The degree node carries the exact heading and is read on focus. The
        // cardinal node carries the live region.
        //
        // The live region rides the cardinal label because that Text's content
        // already changes exactly eight times per rotation — the cadence we
        // want to announce at — whereas the degree description changes ~360
        // times and would make TalkBack narrate continuously.
        //
        // An earlier attempt parked the live region on a zero-size Box instead.
        // That silently announces nothing: Compose builds the accessibility
        // node map by intersecting each node's touch bounds with the remaining
        // uncovered region, and a 0x0 rect yields an empty Region, so the node
        // is dropped from the tree entirely. Bounds must be non-empty for a
        // node to exist for a screen reader at all.
        Text(
            text       = "$rounded°",
            color      = c.textPrimary,
            // Normal, not Light: hairline strokes disappear under glare,
            // which is precisely when this number matters most.
            fontSize   = 52.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            lineHeight = 52.sp,
            modifier   = Modifier.semantics { this.contentDescription = description },
        )
        Text(
            text          = fullLabel,
            color         = c.textPrimary,
            fontSize      = 16.sp,
            letterSpacing = 2.sp,
            modifier      = Modifier.semantics {
                this.contentDescription = sector
                // Polite, not Assertive: a heading change must never interrupt
                // whatever the user is already being told.
                liveRegion = LiveRegionMode.Polite
            },
        )

        // FlowRow, not Row: the worst case here is `TRUE · 4.2° DECL` beside a
        // confidence warning, which is about 29 characters of letter-spaced
        // monospace and overflows a 360 dp-class screen. A plain Row clips it
        // with no warning at build time and no symptom on the test device,
        // which is the kind of thing that only ever shows up in a review
        // screenshot from a phone nobody here owns.
        FlowRow(
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Mode tag — the only control on this screen. sizeIn before
            // clickable so the 48.dp touch target is real, not just visual.
            Text(
                text          = if (trueActive) "TRUE" else "MAG",
                color         = c.textSecondary,
                fontSize      = 12.sp,
                letterSpacing = 1.sp,
                fontFamily    = FontFamily.Monospace,
                modifier      = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onModeTagClick)
                    .wrapContentSize(Alignment.Center),
            )
            if (trueActive) {
                Text(
                    text          = "· %.1f° DECL".format(declination),
                    color         = c.textSecondary,
                    fontSize      = 12.sp,
                    letterSpacing = 1.sp,
                    fontFamily    = FontFamily.Monospace,
                    // FlowRow's verticalArrangement positions LINES, not items
                    // within a line; items are top-aligned. Without this the
                    // short texts sit flush with the top of the 48dp mode tag.
                    modifier      = Modifier.align(Alignment.CenterVertically),
                )
            }
            confidence?.let { (text, color) ->
                // Tappable, because a warning the user cannot act on is only
                // half an answer: this line is where they find out the heading
                // is suspect, so it is where "what do I do about it" has to
                // live. NO SENSOR SIGNAL is excluded — calibration cannot fix
                // a sensor that is not reporting, and offering it there would
                // send the user through a figure eight for nothing.
                val actionable = accuracy != CompassAccuracy.NO_SIGNAL
                Text(
                    text          = text,
                    color         = color,
                    fontSize      = 12.sp,
                    letterSpacing = 1.sp,
                    fontFamily    = FontFamily.Monospace,
                    modifier      = if (actionable) {
                        Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(onClick = onOpenCalibration)
                            .wrapContentSize(Alignment.Center)
                            .semantics {
                                role = Role.Button
                                contentDescription = "$text. Open calibration help"
                            }
                    } else {
                        Modifier
                    }.align(Alignment.CenterVertically),
                )
            }
        }

        // ── Turn guidance, on its own line ────────────────────────────────
        // Deliberately NOT in the row above. That row answers "how much should
        // I trust this number" — the mode tag and the confidence warning are
        // both about trust. A turn instruction is a different kind of thing
        // entirely, and styling it identically at 12 sp monospace made a
        // navigation delta and a sensor-health warning compete for the same
        // glance.
        //
        // The rose shows which way to turn geometrically, and the magnitude now
        // sits beside the marker there. This line stays anyway: the readout is
        // where the eye already is, the rose is far below it, and this is the
        // only form of the instruction a screen reader can convey at all.
        // ── Reserved slot, occupied or not ────────────────────────────────
        // The height is unconditional. Letting this line appear and disappear
        // made the whole column — degree readout, rose, everything — jump the
        // instant the lock was tapped, so the control the user just hit moved
        // out from under their finger. A readout that shifts when you touch it
        // is worse than one that carries an empty row.
        Spacer(modifier = Modifier.height(6.dp))
        // Fades rather than vanishing, matching the rim marker — an accidental
        // unlock is invisible if the element it removes disappears between two
        // frames. See Constants.Ui.LOCK_FADE_MS.
        var lastLocked by remember { mutableFloatStateOf(0f) }
        lockedBearing?.let { lastLocked = it }
        val lineAlpha by animateFloatAsState(
            targetValue = if (lockedBearing != null) 1f else 0f,
            animationSpec = chromeSpec(Constants.Ui.LOCK_FADE_MS),
            label = "lockLineFade",
        )

        Box(
            modifier = Modifier.height(LOCK_LINE_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            if (lineAlpha > 0.01f) {
                val locked = lockedBearing ?: lastLocked
                val delta = shortestArcDelta(locked, azimuth)
                val off = abs(delta).roundToInt()
                val onCourse = abs(delta) <= Constants.Heading.LOCK_ON_COURSE_DEG
                Text(
                    text = when {
                        onCourse -> "ON COURSE"
                        delta > 0 -> "$off° RIGHT"
                        else -> "$off° LEFT"
                    },
                    color = (if (onCourse) c.levelOk else c.textSecondary)
                        .copy(alpha = lineAlpha),
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * Maps sensor confidence to the one line the user should see, or `null` when
 * the heading is trustworthy — silence is the "everything is fine" state, so
 * the warning colours never become background noise the eye learns to skip.
 */
private fun confidenceNote(
    accuracy: CompassAccuracy,
    c: AppPalette,
): Pair<String, Color>? = when (accuracy) {
    CompassAccuracy.HIGH, CompassAccuracy.MEDIUM -> null
    CompassAccuracy.LOW        -> "LOW ACCURACY"         to c.levelWarn
    CompassAccuracy.UNRELIABLE -> "CALIBRATE · FIGURE 8" to c.levelWarn
    CompassAccuracy.NO_SIGNAL  -> "NO SENSOR SIGNAL"     to c.levelCritical
}
