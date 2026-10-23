package com.brewingbytes.gemmory.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Strings
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.ui.withFigures
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.IconAction
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import com.brewingbytes.gemmory.ui.moveAccessibilityFocus
import com.brewingbytes.gemmory.ui.returnAccessibilityFocus
import kotlinx.coroutines.delay

const val PAYWALL_TITLE_TAG = "paywall-title"

/**
 * The paywall (design system, Paywall; Phase 5 D6): close visible immediately at the top-left, five perks (the fifth, the placement tools, #20), Yearly
 * (pre-selected, with its per-week price) and Weekly base plans, Start VIP, the one-time "Just remove ads" row
 * (hidden once owned) and the renewal fine print. No countdowns, no strike-through prices; every price comes from
 * Play. The content column is at most 480dp wide (D22) and scrolls, so 200% font wraps instead of clipping.
 *
 * TalkBack focus (D19) moves to the title on open and back to the node tagged [returnFocusTo] (what opened it) on close.
 */
@Composable
fun PaywallScreen(
    prices: PricesUi,
    purchase: PurchaseUi,
    entitlements: Entitlements,
    onBuy: (product: String) -> Unit,
    onRetryPrices: () -> Unit,
    onClose: () -> Unit,
    returnFocusTo: String? = null,
) {
    var yearly by rememberSaveable { mutableStateOf(true) }
    val view = LocalView.current
    val returnTo by rememberUpdatedState(returnFocusTo)
    LaunchedEffect(Unit) {
        delay(OPEN_FOCUS_DELAY_MS)
        view.moveAccessibilityFocus(PAYWALL_TITLE_TAG)
    }
    DisposableEffect(Unit) {
        onDispose { returnTo?.let { view.returnAccessibilityFocus(it, cancelled = { false }) } }
    }
    val close by rememberUpdatedState(onClose)
    // Success closes the paywall; the gallery shows its line.
    LaunchedEffect(purchase is PurchaseUi.Succeeded) { if (purchase is PurchaseUi.Succeeded) close() }

    Box(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        val strings = strings()
        val copy = BillingCopy(strings)
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(GemIcons.Close, stringResource(R.string.action_close), onClose, Modifier.testTag("paywall-close"))
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    copy.title,
                    style = GemType.display.copy(fontSize = 30.sp),
                    color = GemColors.Bone,
                    modifier = Modifier.testTag(PAYWALL_TITLE_TAG).semantics { heading() },
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { copy.perks.forEach { Perk(it) } }
                Spacer(Modifier.height(24.dp))
                when (prices) {
                    PricesUi.Loading -> Column(
                        Modifier.semantics { contentDescription = strings.text(R.string.billing_loading_prices) },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        repeat(2) { Placeholder() }
                    }
                    PricesUi.Failed -> {
                        OfflineLine(copy.pricesFailed)
                        Spacer(Modifier.height(12.dp))
                        QuietButton(stringResource(R.string.action_try_again), onRetryPrices, Modifier.testTag("paywall-retry"))
                    }
                    is PricesUi.Ready -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlanRow(stringResource(R.string.billing_plan_yearly), yearlyLine(strings, prices.prices.yearly), selected = yearly, tag = "plan-yearly") { yearly = true }
                        PlanRow(stringResource(R.string.billing_plan_weekly), weeklyLine(strings, prices.prices.weekly), selected = !yearly, tag = "plan-weekly") { yearly = false }
                    }
                }
                Spacer(Modifier.height(16.dp))
                val message = when (purchase) {
                    is PurchaseUi.Pending -> copy.pending(purchase.product)
                    PurchaseUi.Failed -> copy.purchaseFailed
                    else -> null
                }
                if (message != null) {
                    Text(
                        message,
                        style = GemType.body,
                        color = GemColors.Bone,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("paywall-message")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                val ready = prices as? PricesUi.Ready
                val vipPending = purchase is PurchaseUi.Pending && purchase.product != Products.REMOVE_ADS
                PrimaryButton(
                    copy.startVip,
                    onClick = { onBuy(if (yearly) Products.VIP_YEARLY else Products.VIP_WEEKLY) },
                    enabled = ready != null && !vipPending,
                    held = purchase is PurchaseUi.Launching,
                    modifier = Modifier.fillMaxWidth().testTag("paywall-start"),
                )
                val removeAds = ready?.prices?.removeAds
                if (removeAds != null && !entitlements.removeAds) {
                    Spacer(Modifier.height(12.dp))
                    RemoveAdsRow(
                        removeAds,
                        enabled = purchase !is PurchaseUi.Launching && purchase != PurchaseUi.Pending(Products.REMOVE_ADS),
                    ) { onBuy(Products.REMOVE_ADS) }
                }
                Spacer(Modifier.height(16.dp))
                Text(copy.finePrint, style = GemType.label, color = GemColors.Bone, modifier = Modifier.testTag("paywall-fine-print"))
            }
        }
    }
}

private const val OPEN_FOCUS_DELAY_MS = 400L

private val mono = SpanStyle(fontFamily = GemType.mono.fontFamily, fontWeight = GemType.mono.fontWeight, fontFeatureSettings = "tnum")

/** "$29.99 / year ($0.58 a week)", prices in mono (figures only, never labels). */
private fun yearlyLine(s: Strings, price: Price): AnnotatedString {
    val perWeek = BillingCopy.perWeek(price)
    return withFigures(BillingCopy(s).yearly(price), listOf(price.formatted, perWeek), mono)
}

private fun weeklyLine(s: Strings, price: Price): AnnotatedString = withFigures(BillingCopy(s).weekly(price), listOf(price.formatted), mono)

/** A perk line with its patina square bullet, centered on the first line when the text wraps (200% font). */
@Composable
private fun Perk(text: String) {
    val lineHeight = with(LocalDensity.current) { GemType.body.lineHeight.toDp() }
    Row {
        Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).background(GemColors.Patina))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = GemType.body, color = GemColors.Bone)
    }
}

/** A plan row: felt-deep, 12dp radius, lamp outline when selected ("this one"). */
@Composable
private fun PlanRow(label: String, price: AnnotatedString, selected: Boolean, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, GemColors.Lamp, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .feltGround(LocalFelt.current.deep)
            .padding(16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = GemType.button, color = GemColors.Bone)
        Text(price, style = GemType.body, color = GemColors.Bone)
    }
}

/** Prices loading (D6): a plan row's shape in felt-raised, nothing to read. */
@Composable
private fun Placeholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LocalFelt.current.raised),
    )
}

/** The quiet "Just remove ads · $X once" row: felt-raised with bone text, the price in mono. */
@Composable
private fun RemoveAdsRow(price: Price, enabled: Boolean, onClick: () -> Unit) {
    val raised = LocalFelt.current.raised
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) raised else raised.copy(alpha = 0.55f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("paywall-remove-ads"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            withFigures(BillingCopy(strings()).removeAds(price), listOf(price.formatted), mono),
            style = GemType.button,
            color = if (enabled) GemColors.Bone else GemColors.Bone.copy(alpha = 0.55f),
        )
    }
}

/** Offline wording on plain felt: bone text after a 6dp `fault` square (design system, Colors). */
@Composable
private fun OfflineLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("paywall-prices-failed")) {
        Box(Modifier.size(6.dp).background(GemColors.Fault))
        Spacer(Modifier.width(8.dp))
        Text(text, style = GemType.body, color = GemColors.Bone)
    }
}
