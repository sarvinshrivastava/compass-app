# Minimal Compass — Store, Monetisation and Launch Report

Package: `com.pubpeb.compass`
Prepared: 2026-09-05
Scope: store listing, pricing, India finance and admin, user acquisition. Code stability is covered separately.

---

## Recommendation

**Publish free, and charge through a single one-time in-app "Pro" unlock — not a paid-upfront listing.** The deciding fact is not psychology or taste, it is a one-way door in Play Console: a paid app can be made free at any time, but **a free app can never be made paid**. An in-app product, by contrast, can be introduced, repriced, discounted, or withdrawn whenever you like. Free-plus-IAP therefore keeps every option open that paid-upfront closes, while also keeping the install funnel that Play's ranking, "similar apps" and search surfaces all feed on. The market evidence points the same way: Fulmine Software ships the same compass twice, and the free edition has roughly 1.1 million installs against 8,400 for the $2.49 paid twin — a 130:1 gap from a developer who already has an audience, which you do not. Ship v1.0 completely free so the first fourteen days of closed testing buy you retention data instead of billing code, then add the Pro unlock in v1.2 with everyone who installed before that date grandfathered in. Be clear-eyed about the number at the end of it: at a realistic 2,000 first-year installs and a utility-grade conversion rate, this app earns somewhere between one and four thousand rupees in year one — less than the cost of the developer account plus a weekend. Nothing in this report changes that. What free-plus-IAP buys you is the fat tail: compass apps genuinely do reach hundreds of thousands of installs, and only the free path can ever get there.

---

## A. Paid vs Free

### A.1 What the category actually looks like

Compass is one of the most saturated commodity utilities on Play. The incumbents are large, old, free, and mostly ad-supported.

| App | Package | Installs | Rating | Model |
|---|---|---|---|---|
| Digital Compass (KTW Apps) | `com.ktwapps.digitalcompass` | ~16M | — | Free |
| Digital Compass (Axiomatic) | `com.vincentlee.compass` | 10M+, ~410K in last 30 days | 4.66 / 572K reviews | Free |
| Compass Maps | `com.directionalcompass.compassmaps` | ~5.4M | — | Free |
| Compass: Accurate Compass | `apps.r.compass` | 5M+ | 4.66 / 43,653 | Free |
| Compass (Fulmine Software) | `com.fulminesoftware.compass` | ~1.1M, ~700 in last 30 days | 4.72 / 17K | Free, upsell to Pro |
| Compass Pro (Fulmine Software) | `com.fulminesoftware.compass.pro` | ~8.4K | — | Paid, $2.49 |
| Compass Steel 3D | `com.simplywerx.compass3d` | — | — | Free, ad-free, donation-supported |

Sources: AppBrain listings for each package, plus the Play listings themselves (see Sources).

Three things follow from that table.

**The free tier is not merely dominant, it is the whole market.** Across Play as a whole roughly 97% of apps are free and free apps generate about 98% of store revenue; in compass specifically, every app above a million installs is free. There is no premium compass segment for you to enter, because there is no premium compass segment.

**Fulmine's two listings are a controlled experiment and the result is brutal.** Same developer, same code, same store presence, one free and one at $2.49: 1.1M against 8.4K, a ratio of about 131 to 1. The paid listing is not a business, it is a tip jar with an install button.

**The incumbents are beatable on quality, not on volume.** Most of them are cluttered, ad-laden, and ask for permissions a compass has no business asking for. Minimal Compass's actual competitive position is the small, discerning slice of users who go looking specifically for something clean — the same slice that keeps Compass Steel 3D alive on donations. That is a real audience. It is also a small one.

### A.2 The five options, honestly

**Free, no monetisation.** Maximum installs, maximum ratings, maximum ranking signal, zero revenue and zero optionality — because you can never add a price later, only an IAP. This is strictly dominated by free-plus-IAP, which is the same thing with a door left open.

**Free plus one-time IAP unlock.** Full install funnel preserved. Conversion for utilities with low session frequency runs well below the cross-category freemium median (RevenueCat puts the median day-35 download-to-paid conversion for freemium apps at 2.1%; a compass, which people open for eight seconds and forget for three weeks, should be modelled at 0.5–1%). Requires Play Billing work and a merchant account. **This is the recommendation.**

**Paid upfront.** Collapses installs by one to two orders of magnitude. Kills the ratings flywheel — you cannot get to fifty ratings if you cannot get to five hundred installs. Play's discovery surfaces (search ranking, "similar apps", "you might also like") are install- and retention-weighted, so a paid listing is invisible by construction. It also puts you on the wrong side of Google's refund policy change: all refunds now come out of developer payouts, regardless of when they are granted. The one genuine argument for paid — that you can always drop the price to zero later — is real, but going free after a cold paid launch does not recover the early install velocity that Play's ranking rewards, and it leaves you with no clean way to distinguish your original buyers.

**Free plus ads.** Wrong on two counts. Commercially, a compass session is measured in seconds; you need tens of thousands of daily actives before banner revenue clears a few hundred rupees a day, and you will not have them. Structurally, it reverses a deliberate architectural decision — the manifest explicitly strips `AD_ID`, `ACCESS_ADSERVICES_AD_ID` and `ACCESS_ADSERVICES_ATTRIBUTION` with `tools:node="remove"`, and disables Firebase's ad-personalisation signals, precisely so the Data Safety section reads clean. Adding ads means putting all of that back, rewriting the Data Safety disclosures, and destroying the one thing that differentiates this app from the sixteen-million-install incumbent.

**Subscription.** No. There is no recurring cost, no recurring value, and no recurring reason to open the app. A subscription on a compass reads as contempt for the user and will be reviewed accordingly.

### A.3 Expected revenue, without flattery

Year-one installs for a well-listed, well-screenshotted utility from an unknown developer with no audience and no ad spend. These are the honest bands, not aspirations.

| Scenario | Year-1 installs | Pro conversions at 0.8% | Net revenue (blended ~₹110/sale after fee) |
|---|---|---|---|
| Cold (no coverage, no luck) | 400 | 3 | ~₹350 |
| Realistic | 2,000 | 16 | ~₹1,800 |
| Good (one channel lands) | 8,000 | 64 | ~₹7,000 |
| Outlier (real press or a Play editorial pickup) | 50,000 | 400 | ~₹44,000 |

The blended figure assumes ₹99 in India, $2.99 in the US and PPP-adjusted prices elsewhere, at Google's 15% service fee, with a rough 40/60 India-to-rest-of-world split.

Read the realistic row again: about eighteen hundred rupees in the first year, against a ₹2,100 account fee. **This app will not pay for itself in year one and probably not in year two.** If the goal is income, the correct advice is to build something else. If the goal is to ship a good thing, learn the whole publishing pipeline end to end, and hold a lottery ticket on the fat tail, then this plan is the right one — but go in with the number above in your head, not a better one.

A note on the tail, because it is the only reason any of this is worth doing: `com.vincentlee.compass` is pulling 410,000 installs a month, today, for a free compass. The distribution of outcomes in this category is extremely skewed. Free-plus-IAP is the only option that keeps you in the draw.

### A.4 What to gate, and what must never be gated

The paywall boundary decides whether users experience Pro as a fair ask or as a hostage situation. Three rules:

1. **The compass itself is never gated.** Heading, cardinal, rose animation, and the calibration warning stay free forever. If someone opens the app and cannot read a bearing without paying, you have built a different, worse product.
2. **Daylight mode is never gated.** It is a legibility feature — arguably an accessibility one. Charging to make the screen readable in sunlight is the kind of thing that earns one-star reviews with the word "scam" in them.
3. **True north stays free.** Charging for "the compass points the right way" is indefensible for an app whose entire pitch is honesty about what it knows.

That leaves a clean Pro bundle:

- **AR camera overlay** — the genuinely novel, expensive, visually demonstrable feature. This is the anchor.
- **Bubble level** (page 1 of the pager) — a second, separate instrument. Gating it gives you a natural, non-intrusive discovery surface: the swipe still works, it just lands on a preview with an unlock card instead of a nag dialog.
- **Home-screen widget** — a convenience, and the least contentious thing in the bundle.

Price it as one purchase, name it plainly ("Unlock everything — one payment, forever, no subscription"), and say in the Pro sheet that the free compass will never be crippled. That sentence converts better than any feature list.

### A.5 Grandfathering, and the Play Billing work

Because v1.0 ships with AR, level and widget free, adding a paywall in v1.2 would retroactively remove features from existing users. Do not do that. Grandfather them:

```kotlin
val installedAt = packageManager
    .getPackageInfo(packageName, 0)
    .firstInstallTime
val isFounder = installedAt < PRO_PAYWALL_EPOCH_MS
```

`firstInstallTime` is local, offline, needs no permission and no network, and is exactly the semantics you want. Announce it in the v1.2 release notes ("if you were here before today, you keep everything"). It costs about fifteen lines and buys you goodwill you cannot buy any other way.

**Play Billing work implied** (all of it in v1.2, none in v1.0):

- **Billing Library 8 or later is mandatory.** Since 31 August 2026, Play blocks new apps and updates built against Billing Library 7 or earlier. Version 9.0.0 shipped 19 May 2026 and is current — start there, not on 8.
- One **non-consumable in-app product** (`INAPP` type), id something like `pro_unlock`. Not a subscription.
- `BillingClient` connect and reconnect handling; `queryProductDetailsAsync`; `launchBillingFlow`; `PurchasesUpdatedListener`; **`acknowledgePurchase` within three days** or Play auto-refunds the purchase.
- **`queryPurchasesAsync` on every cold start** — this is the entitlement source of truth. It restores the purchase on a reinstall or a new device with no account system of your own, which matters a great deal for an app that deliberately has no accounts.
- Handle `USER_CANCELED`, `ITEM_ALREADY_OWNED`, `SERVICE_DISCONNECTED`, and the offline case (cache the entitlement locally, fail open rather than locking a paying user out on a train).
- A **payments/merchant profile** on the Google payments centre, which for an India-based seller pulls in the BillDesk KYC described in section B.
- **Update the Data Safety form** — purchase history is a declarable data type.

Realistically this is one to two days of work for someone who has not done it before, plus testing against a licence-tester account.

---

## B. India-specific finance and admin

**A caution before the detail.** Everything in B.4 and B.5 is Indian tax law applied to a fact pattern (individual, digital goods, foreign marketplace, mixed domestic and export sales) that has genuine unsettled edges. I can tell you accurately what the rules say and where they bite; I cannot tell you what your specific liability is, and neither can any web page. Where this report says "see a CA", that is not hedging — it is the correct answer.

### B.1 The registration fee, paid from India

- **US$25, one time, no annual renewal.** In INR the charge lands at roughly ₹2,100–₹2,300 depending on the card rate on the day, plus whatever cross-border markup your bank adds.
- Google accepts **Visa and Mastercard issued by Indian banks**. The single most common failure is a card with international transactions disabled — enable them in your bank's app first, and make sure the card's per-transaction international limit is above the amount. RuPay is generally not accepted for this.
- The fee is non-refundable and is per Google account, not per app.

### B.2 Identity and address verification (personal account)

Google's developer verification programme rolled out to all Play Console developers through 2026, and a new personal account cannot publish without completing it.

**What India-based individuals must supply** (per Google's India-specific document list):

- **Photo ID** — Indian passport, driving licence, Voter ID, or PAN card. Valid, in colour, clear, and a photograph of the original rather than a photocopy. No fields obscured.
- **Address proof** — a government photo ID that carries the address, or a utility bill (electricity, water, gas, internet, cable), a home or health insurance statement, or a bank/credit-card statement.
- **Phone verification** and a **public support email address** that will be shown on your store listing.
- **PAN** is required on the payments profile.

**The rule that fails people:** every string must match exactly across the government ID, the address proof, the Play Console profile and the payments profile — full legal name, spelling, and address. A middle name present on the PAN card and absent from the Console profile is enough to bounce the submission. Fix the Console profile to match the documents before you submit, not after.

**Timing:** individual accounts typically clear in **2–5 business days**. Organisation accounts take 5–10 business days and additionally need a D-U-N-S number, which can take 30 days or more to obtain — one of several reasons a solo developer should stay on a personal account.

There is a related but separate programme worth knowing about: Android developer verification for apps distributed **outside** Play. Enforcement begins **30 September 2026** in Brazil, Indonesia, Singapore and Thailand and expands globally through 2027. Play Console developers who have completed identity verification are automatically registered for their eligible apps, so publishing through Play covers you here. It only becomes your problem if you later distribute an APK directly.

### B.3 Payments profile, merchant account and payouts

- A **Google payments profile** is required to pay the $25 and to receive any money. A **merchant account** in the Google payments centre is required specifically to sell paid apps or in-app products; a free app with no IAP does not need one. Create it anyway during setup, because it is the long pole.
- **RBI PA-CB KYC with BillDesk.** India-based Play merchants must complete a KYC verification with BillDesk to satisfy the Reserve Bank's Payment Aggregator – Cross Border rules. Google's guidance states existing merchants must complete verification by **15 September 2026** or have sales outside India paused, and that **new merchants starting in 2026 must verify before selling internationally**. This is ten days away as of writing and is the single most time-sensitive item in this report. Start it the day the account opens, even though your IAP will not ship until v1.2.
- **Payout mechanics.** Google pays out on or around the **15th of each month** for the previous month's sales, once the balance clears the **US$100 minimum**. Disbursements to Indian developers are processed by **Indiaideas / BillDesk**, and developers with India as their legal country receive **two separate disbursements** — one covering buyers in India, one covering buyers outside India. Expect a small INR test deposit before the first real payout. Money arrives in **INR** in your Indian bank account.
- **The $100 threshold is the practical trap at your revenue level.** At the "realistic" scenario in A.3 you will accrue roughly ₹150 a month. You will not clear US$100 in year one, which means you will not be paid in year one. The balance carries forward; it does not expire. Plan for the first payout to arrive somewhere in year two or three.
- **FIRC / eFIRA.** For the foreign-buyer disbursement you can request a Foreign Inward Remittance Certificate: you request it from Google, Google passes the data to BillDesk, BillDesk coordinates with Citibank, and the certificate is issued to you. You need it as proof of export receipts if you ever claim export treatment under GST or need to substantiate foreign income. Request it; do not assume you can reconstruct it later.

### B.4 Google Play's service fees

**Today, for an India-based developer selling to Indian and rest-of-world buyers:**

- **15%** on the first US$1,000,000 of earnings per calendar year.
- **30%** above that.
- **15%** flat on auto-renewing subscriptions regardless of volume.

You will be on 15% permanently. The $1M threshold is not a live concern.

**The 2026 restructure, and whether it touches you.** From **30 June 2026** Google split the fee into a service fee plus a billing fee in the **US, UK and EEA**: 10% service fee on the first $1M plus a 5% Google Play billing fee, so **15% effective** — the same number, differently labelled. Above $1M it becomes 20%+5% for new installs and 25%+5% for existing installs. The rollout is regional and **follows the buyer's country, not the developer's**: Australia from 30 September 2026, Japan and Korea from 31 December 2026, and the **rest of world — India included — on 30 September 2027**. So your Indian and most international sales stay on the old 15% headline until late 2027, while any US, UK or EEA buyers are already on the new split. Either way you pay 15%.

**Fee reductions that could apply:**

- **User choice billing** is available in India for both games and non-games. If a user pays through your alternative billing system rather than Google's, the service fee is **reduced by 4 percentage points** (so 15% becomes 11%). Not worth it at your volume — you would be integrating and reconciling a second payment processor, and taking on chargeback and refund handling yourself, to save about four rupees per sale.
- The Play Media Experience and Apps/Games experience programmes reduce fees further but are aimed at large partners. Not applicable.
- **Refunds now come out of your payouts** in all cases, not just those inside 48 hours. At ₹99 this is noise, but it means a wave of refunds cannot be absorbed by Google on your behalf.

### B.5 GST, TDS and income tax

**Google's role.** For sales to Indian customers, Google acts as the marketplace and **determines, charges and remits GST on behalf of developers** for paid apps and in-app purchases. Prices you set on Play must be **tax-inclusive** — the number the Indian buyer sees already contains the 18% GST on digital goods, and it is deducted before your share is computed. You do not separately invoice or remit GST on those consumer sales.

**Do you need a GSTIN?** Google's own documentation is explicit that this is your call to make, not theirs: an India-based developer is "responsible for determining whether you need to obtain a GSTIN". The shape of the answer:

- The general registration threshold for a service provider is **₹20 lakh** aggregate turnover in a financial year (₹10 lakh in the special-category states of Manipur, Mizoram, Nagaland and Tripura). You are nowhere near it.
- Section 24 of the CGST Act imposes **compulsory registration** on persons supplying through an e-commerce operator that collects TCS. However, **Notification 65/2017 – Central Tax (15 November 2017)** exempts suppliers of *services* (other than section 9(5) services) through such an operator from that compulsory registration, provided aggregate turnover stays under the ₹20 lakh / ₹10 lakh threshold. On the ordinary reading, a small Indian app developer selling through Play falls inside that exemption.
- The complication is **exports**. Supplies to buyers outside India are inter-state supplies, and the standard position is that exporters must register regardless of turnover — but who the "recipient" is when Google is the merchant of record for foreign sales is exactly the sort of question that has more than one defensible answer.
- **You will also be charged GST by Google on the service fee** it bills you, and if you are unregistered you cannot claim input credit on it. That is a real cost, not a wash.

**This is the single item in this report where you must consult a CA before you take a position**, and it costs a few thousand rupees to do so. Bring: your expected revenue, the India-versus-export split, and Google's India tax documentation. Do not register for GST on a hunch — registration brings monthly and annual return filing obligations that dwarf the revenue at stake, and voluntarily registering is much easier than getting out again.

**What Google withholds.** Per Google's India tax documentation, Google deducts and deposits **income withholding tax (TDS)** under **section 393(1) of the Income Tax Act, 2025** — the successor to section 194-O of the 1961 Act, in force from 1 April 2026 — and **GST TCS** on paid apps, games and in-app purchases. The e-commerce TDS rate was cut from 1% to **0.1%** with effect from 1 October 2024 and carries through into the new section. It is a credit against your final liability, not an extra tax; it shows up in your Form 26AS / AIS and you claim it when you file. Submit your **PAN** to the payments profile so the deduction is at the correct rate rather than the penal one.

**Equalisation levy — dead, ignore it.** The 2% levy on e-commerce supply was abolished with effect from **1 August 2024** and the 6% levy on online advertising from **1 April 2025**. No equalisation levy is in force in India. Any advice you read that mentions it is stale.

**Income tax on the earnings.** App revenue from your own product is **business income**, not "income from other sources" and not salary.

- **ITR-3** if you maintain books and claim actual expenses.
- **ITR-4 (Sugam)** if you elect presumptive taxation. Section **44AD** (business, threshold ₹3 crore where cash receipts are under 5% of gross receipts, otherwise ₹2 crore) is the usual fit for app *sales*; section **44ADA** (notified professions, threshold ₹75 lakh / ₹50 lakh) is the usual fit for *software services* you bill clients for. Which one covers self-published app revenue is genuinely argued both ways among practitioners.
- Presumptive is almost certainly right at your scale — declare the presumed profit, skip the books, file ITR-4, done in an evening.
- If this is a side project alongside salaried employment, the app income is **added to** your salary income; you cannot file ITR-1 once you have business income.

**Where you need a CA and where you do not.**

| Task | CA needed? |
|---|---|
| Paying the $25 fee, verification, payments profile, BillDesk KYC | No |
| Setting prices, understanding the 15% fee | No |
| Filing ITR-4 under presumptive taxation with only salary plus small app income | No — the ITR-4 utility handles it |
| Deciding whether to register for GST, given exports through a merchant-of-record marketplace | **Yes, and before you decide** |
| Claiming the 0.1% TDS credit and reconciling with AIS | No, but easy to get wrong the first year |
| If revenue ever approaches ₹20 lakh, or you incorporate, or you start invoicing clients too | **Yes** |

---

## C. Pricing

### C.1 Mechanics on Play

- Google Play has **no fixed price-tier ladder**. You set an arbitrary price per country in local currency. Play generates local prices from your base price using current exchange rates and country-specific rounding patterns, and you can override any of them by hand.
- **Pricing templates were discontinued on 27 October 2025.** Bulk per-country pricing now requires the Play Developer API or external tooling. With one in-app product across a handful of overridden countries, do it by hand in the Console — the API is not worth wiring up for this.
- Minimum prices vary by country and Google lowered the floor in India and sixteen other markets some years ago; the India minimum is around the equivalent of **US$0.21**. You will not be near the floor.
- Prices are **tax-inclusive** in India — set ₹99 and ₹99 is what the buyer pays.
- **Paid apps can go on sale** for 1–8 days at a time, with at least 30 days between sales. In-app products can be discounted through Play Console's promotions, and **promo codes** are available there — useful for handing reviewers a free unlock without giving them your APK.

### C.2 The recommended price

**One in-app product, `pro_unlock`, non-consumable.**

| Market | Price | Reasoning |
|---|---|---|
| India | **₹99** | The canonical Indian one-tap digital price. It sits under the ₹100 mental barrier, matches what Indians pay for a one-time app unlock, and asks for no deliberation. |
| United States | **$2.99** | One notch above the $2.49 the paid compass comparables sit at. At these volumes the conversion difference between $2.49 and $2.99 is statistical noise and the revenue difference is 20%. |
| UK | **£2.49** | |
| Eurozone | **€2.99** | |
| Tier-3 markets — Brazil, Indonesia, Turkey, Philippines, Vietnam, Egypt, Nigeria, Mexico, Pakistan, Bangladesh, Sri Lanka, Nepal | **35–45% of the USD price** | Override manually. Play's auto-conversion holds dollar value constant, not purchasing power, and the gap between an auto-converted and a PPP-appropriate price in these markets runs 2–3×. Left alone, Play would price this at roughly ₹260 in India — nearly triple what the market bears. |

**On ₹49 vs ₹99 vs ₹149.** ₹49 is below the line where a price reads as a product rather than a trinket; it nets about ₹42 after fees and invites the "why is this even paid" reaction without materially raising conversion. ₹149 is a defensible ask for a tool and would suit an app with a support burden and a roadmap — but it invites comparison shopping against free incumbents that a ₹99 impulse purchase never triggers. ₹99 is the right answer for an unknown developer selling a one-time unlock on a commodity utility: it converts on impulse, it signals "real product", and it is small enough that nobody asks for a refund.

**Price differently in India versus the US — yes, decisively.** Not out of charity but out of arithmetic: identical dollar pricing in India would price you out of your home market entirely, and India is where your first testers, first reviews and first word of mouth will come from.

**Launch discount.** Do not discount at launch. A one-time unlock at ₹99 is already an impulse price, and an introductory discount on a brand-new product with no reviews signals desperation rather than value. Save the discount lever for a real moment — a major feature release, or a festive-season push in India — where it has a story attached. Do use promo codes freely for reviewers, testers and anyone who writes about the app.

---

## D. Store listing assets — production spec

### D.1 Current Google Play requirements

| Asset | Requirement |
|---|---|
| **App icon** | 512 × 512 px, **32-bit PNG with alpha**, max 1024 KB. Google applies its own masking and shadow, so keep the meaningful content inside a centred circle of roughly 66% of the canvas — anything in the corners can be clipped. Do not bake in your own rounded corners or drop shadow. |
| **Feature graphic** | Exactly 1024 × 500 px, **JPEG or 24-bit PNG, no alpha**. Required for every listing. |
| **Phone screenshots** | **Minimum 2, maximum 8.** JPEG or 24-bit PNG, no alpha, max 8 MB each. Minimum side 320 px, maximum side 3840 px, and the longer side may not exceed twice the shorter. **For eligibility for Play's recommendation surfaces you need at least 4 screenshots at 1080 px or better, in 16:9 landscape (min 1920 × 1080) or 9:16 portrait (min 1080 × 1920).** Ship 8 at 1080 × 1920. |
| **7" tablet screenshots** | Not strictly required, but required in practice if you distribute to tablets. Google recommends at least 4, 16:9 or 9:16, between 1080 and 7680 px. 1080 × 1920 works. |
| **10" tablet screenshots** | Same rule. 1200 × 1920 is the usual choice. Phone screenshots stretched to tablet aspect get flagged in store quality review — capture real tablet frames. |
| **Promo video** | Optional. YouTube URL only, no extra parameters such as timecodes, ads disabled, video set to public or unlisted, no age restriction. |
| **Text** | Title 30 chars, short description 80 chars, full description 4,000 chars. |
| **Also required** | Privacy policy URL, Data safety form, content rating questionnaire (IARC), target audience declaration, ads declaration, app access declaration, public support email. |

**Tablets: decide deliberately.** This app has a compass rose, a bubble level and a camera overlay — all of which work fine on a tablet and none of which are optimised for one. You have two honest options: opt out of tablet distribution in the device catalogue, or capture four real tablet screenshots from an emulator. Do not upload stretched phone shots.

### D.2 The shot list

Eight screenshots, 1080 × 1920, portrait, in this order. The first two do almost all the work — most users never swipe past them — so the compass and the AR view lead.

Set every frame on a plain background drawn from the app's own palette (deep night ground for frames 1–3 and 5–8, the daylight white for frame 4), with the device frame inset so roughly 20% of the canvas above the phone carries the caption in large, tight, well-tracked type. Keep one type treatment across all eight.

**1 — The compass rose, night palette, holding a clean bearing.**
Capture the flat compass screen with a memorable heading (something like 47° NE), the rose crisp, the `TRUE` tag lit, no dialogs and no warning state.
> **Caption: "A bearing. Nothing else."**

**2 — The AR camera overlay, outdoors.**
Raise the phone against a real horizon — a street, a skyline, a treeline — so the floating cardinal labels sit over an actual scene rather than an office ceiling. This is your most differentiated frame and the one that stops a scroll.
> **Caption: "Raise the phone. The horizon gets labelled."**

**3 — True north, with the declination correction visible.**
The compass screen with the `TRUE` tag active. If the UI can be captured showing the declination value alongside it, use that frame.
> **Caption: "True north, not just magnetic."**

**4 — Daylight mode, on the white high-contrast ground.**
The same compass rose in daylight mode. The whole frame should read as bright — this is the one screenshot where the background of the marketing canvas goes white too, so the contrast switch is obvious at thumbnail size.
> **Caption: "Readable in direct sunlight."**

**5 — The bubble level.**
Page 1 of the pager, bubble slightly off-centre so it reads as live rather than staged, with the pitch and roll degrees visible.
> **Caption: "A spirit level, one swipe away."**

**6 — The calibration prompt.**
The `CALIBRATE · FIGURE 8` state. Counter-intuitive as a marketing frame, but it is the honesty pitch made visible, and it is the single most common complaint about every competitor: they show a confident bearing when the magnetometer is unreliable.
> **Caption: "It tells you when it doesn't know."**

**7 — The home-screen widget.**
A real launcher home screen with the widget placed among ordinary app icons, so its scale and restraint read correctly.
> **Caption: "One tap from the home screen."**

**8 — The settings sheet.**
The sheet open, showing "Auto-switch to AR — raise the phone to open the camera view". Short, and it demonstrates that the clever behaviour is a preference rather than something imposed.
> **Caption: "Everything is a choice. Nothing is a nag."**

**Feature graphic (1024 × 500).** Do not put a phone in it and do not repeat a screenshot. Play crops and scales this asset unpredictably across surfaces, and small text inside it is frequently illegible. Use the compass rose mark alone, large, off-centre-left on the deep night ground, with the wordmark and at most four words to its right — something like "Minimal Compass — true north, nothing else." Keep every element well inside a 60 px margin.

**A copy-compliance warning that applies to all of this.** Do not caption anything "no tracking", "no data collected", "100% offline" or "fully private". The app ships Firebase Analytics and Crashlytics; it makes network calls, and your Data Safety form will say so. Play's metadata policy prohibits inaccurate claims, and a caption that contradicts your own Data safety section is both a policy problem and a credibility problem. **"No ads. No sign-in."** is true, is the thing users actually care about, and is safe.

### D.3 How to capture them, with no design budget

**Capture on the real device, not the emulator, for frames 1–7.** The AR overlay needs a real camera and a real horizon; the magnetometer needs a real magnetometer; the daylight mode needs to be judged with your eyes. Use the emulator only for the tablet set.

```bash
adb shell settings put system screen_off_timeout 2147483647   # save the old value first
adb exec-out screencap -p > shot-01-compass.png
# ... capture all frames ...
adb shell settings put system screen_off_timeout <saved value>
```

Capture on a device with a 1080 × 1920 or 1080 × 2400 panel. If the panel is taller than 16:9, crop to 1080 × 1920 rather than scaling — scaling softens the thin rose ticks, which are the most delicate thing in the UI.

Before capturing: enable Do Not Disturb, turn off the notification shade clutter, and consider the demo-mode status bar so every frame has an identical clean bar:

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# afterwards:
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

**Framing and captions, free.** Several browser tools produce Play-spec output with current device frames and no install: **BrandBird**'s Play Store generator is free with no watermark and no account; **Screenshots Free** offers device frames and batch export without watermarks; **AppLaunchpad** is a long-standing free option; **Screenhance** has a free tier that watermarks exports and a one-time low-cost pass to remove it. Any of them will do. If you would rather own the file, a Figma or Canva artboard at 1080 × 1920 with one text layer and one image layer, duplicated eight times, takes about forty minutes and gives you something you can re-export every release.

**The realistic no-budget path, in order:** capture eight clean PNGs on the device (one hour) → drop them into BrandBird with a single caption style (one hour) → build the feature graphic in the same tool or in Figma from the app's own icon asset (one hour). Three hours, zero rupees. The icon you already have; export it at 512 × 512 with alpha and check it against a circular mask before uploading.

---

## E. Listing copy and ASO

### E.1 How Play weights the fields

Play's ranking reads all three text fields, weighted roughly in this order: **title first and heaviest**, then the **short description**, then the **full description**, which is fully indexed. There is no separate keywords field as on the App Store — the description *is* the keyword field. Play also folds in installs, retention, ratings, uninstall rate and crash rate, which is why the free-vs-paid decision in section A dominates anything you write here.

Two practical consequences. First, "Compass" must appear in the title; it is the query, and no amount of description tuning compensates for its absence. Second, keyword stuffing in the full description actively hurts — Play's metadata policy prohibits "excessive" metadata, and repetitive descriptions get demoted rather than promoted. Aim for each important term appearing two or three times across four thousand characters, in sentences a person would actually read.

**Realistic keyword expectations.** "Compass" is one of the most competitive utility queries on Play, held by apps with five to sixteen million installs and half a million ratings each. You will not rank for it in year one. What you can plausibly rank for is the long tail where the incumbents are weak: *minimal compass, simple compass, compass no ads, true north compass, compass and level, ad free compass, offline compass, compass widget*. Those are where your description's phrasing should be aimed. Note that "qibla" dominates a large adjacent slice of this category's search volume — a genuine, large opportunity, but a different app with different obligations, and out of scope for a listing built on restraint.

**Category.** File under **Tools**. Compass apps appear in both Tools and Maps & Navigation and the top performers rank in Tools; it is also where "no ads, does one thing" positioning is best understood. You can change it later.

### E.2 Title — three variants

```
Minimal Compass
```
15 characters. Pure brand. Cleanest, most memorable, and the right long-term name — but it spends its whole budget on identity and adds no discoverable term beyond "Compass" itself. Choose this only if you intend to build the brand over years.

```
Compass: True North & Level
```
27 characters. Keyword-forward: leads with the query term and picks up "true north" and "level" as secondary matches. Best pure-ASO choice, but it is generic — it sounds like the sixteen-million-install incumbent rather than an alternative to it.

```
Minimal Compass: True North
```
27 characters. **Recommended.** Keeps the brand in the leading position where users and the algorithm both see it, and buys the highest-value secondary keyword. "Minimal" is itself a differentiating search term for exactly the user who would pay you ₹99, and it sets the expectation the app then meets.

Keep the launcher label as `Compass` (it already is) — a short label under an icon is the right choice on a home screen, and Play does not require the two to match.

### E.3 Short description — 80 characters

```
True north compass, bubble level and AR camera view. No ads, no sign-in.
```
72 characters. **Recommended.** Carries four searchable terms and closes on the two promises users in this category most want to hear.

Alternative, if you want positioning over keywords:

```
A compass that opens, shows true north, and gets out of the way.
```
64 characters.

### E.4 Full description — 4,000 characters

```
A compass should open instantly, show you which way you are facing, and then leave you alone. That is the whole idea behind Minimal Compass. There is no sign-up, no onboarding carousel, no banner across the bottom of the screen, and nothing to dismiss before you can read a bearing.

WHAT IT DOES

Points north. A large, legible heading in degrees with its cardinal direction, on a compass rose that turns smoothly and takes the shortest way round when you cross north — no spinning the long way from 359 back to 1.

Finds true north, not just magnetic north. Tap the MAG tag and, with a single coarse location fix, the app applies your local magnetic declination and switches to TRUE. Magnetic north can sit many degrees away from true north depending on where you stand, and for map and navigation work that difference matters. Location is never required: the compass works from the first frame with no permission granted at all, and a location fix only ever improves the reading.

Tells you when it is not sure. Most compass apps display a confident number no matter what the magnetometer is actually doing. This one shows a clear CALIBRATE prompt when the sensor is unreliable, so you know to move the phone in a figure eight rather than trusting a bearing that is quietly wrong.

Labels the world through the camera. Raise the phone toward the horizon and the view fades into a live camera overlay with the cardinal directions floating in place over the real scene, positioned using your device's actual lens field of view. Lower it and you are back on the rose. If you would rather it never did this on its own, switch it off in settings and use the button instead.

Levels a surface. Swipe across for a bubble level with live pitch and roll in degrees. Useful for hanging a picture, setting up a tripod, or checking a shelf.

Stays readable in the sun. A daylight mode swaps the dark interface for a maximum-contrast white one for the moments when you are standing outside in bright light and the usual dark theme has become a mirror.

Sits on your home screen. A small widget puts the compass one tap away. It deliberately does not display a heading, because a widget cannot hold the magnetometer open, and a number captured at an unknown moment in an unknown pose and shown for the next fifteen minutes is a guess dressed up as a fact.

WHAT IT DOES NOT DO

No advertisements, anywhere, ever. No account, no sign-in, no email address. No step counter, no weather panel, no metal detector, no flashlight, no map layer, no news feed. The app asks for exactly two permissions and only when they are needed: approximate location, if you want true north, and the camera, if you want the AR view. Decline either and everything else still works.

BUILT WITH SOME CARE

Written for modern Android with Jetpack Compose. The sensors are registered only while the app is on screen and released the moment it is not, so it is not quietly draining your battery in your pocket. Accelerometer and magnetometer readings are filtered before fusion, and the orientation maths is remapped when you hold the phone upright so the heading stays correct in camera view instead of drifting ninety degrees, which is a mistake a surprising number of compass apps make.

Requires a device with a magnetometer. Most Android phones have one; a few budget models do not, and on those no compass app can work, including this one.

If something is wrong or a bearing looks off, write to the support address on this listing. It reaches a person.
```

That draft is 3,521 characters against the 4,000 limit, leaving room to add a "What's new in this version" paragraph at the top on future releases without breaching the 4,000 limit. It carries *compass, true north, magnetic north, declination, bubble level, spirit level, camera, AR, widget, daylight, calibrate, no ads, offline-capable* naturally, none of them more than three times.

### E.5 Other listing fields

- **Developer name.** Use a stable name you are willing to keep. It appears under every app you ever publish and is itself a searchable field.
- **Support email.** Required and public. Use a dedicated address, not your personal one — it goes on the internet permanently.
- **Privacy policy.** Required. It must be a live URL, must not be a PDF or a Google Doc that requires sign-in, and must describe what the Data safety form declares. A GitHub Pages page in this repo is fine and free.
- **Data safety form.** Declare honestly: **Crash logs** and **Diagnostics** (Crashlytics and Firebase Analytics), **Approximate location** (collected only for the declination fix — declare it as not linked to identity, not shared, and optional), and **Purchase history** once the IAP ships. The manifest's removal of `AD_ID` and the ad-personalisation flags means you can truthfully answer "no" to advertising identifiers, which is worth having. The most common rejection cause here is forgetting that a third-party SDK's collection counts as yours — Firebase's does.
- **Ads declaration.** "This app contains no ads." Answer it truthfully and the store badge follows.
- **App access.** "All functionality is available without special access" — no login, no gated content.
- **Content rating.** Complete the IARC questionnaire; a compass rates as everyone/PEGI 3 in every region.
- **Target audience.** Select adults only, or at minimum do not target under-13s — opting into the Families programme brings a large additional policy surface for no benefit here.

---

## F. Getting users

### F.1 The gate that sets your timeline

Personal Play Console accounts created after **13 November 2023** cannot publish to production until they have run a **closed test with at least 12 testers continuously opted in for at least 14 days**, and then had a production-access application approved. The tester minimum was cut from 20 to 12 in December 2024; the 14 days did not change. Testers who opt in and then drop out before the 14 days elapse do not count, and if someone opts out and back in the clock restarts for them.

The application itself asks about your closed test, your app, and your production readiness. **Review usually takes seven days or less, occasionally longer.** Production and pre-registration tracks stay locked until it clears.

**Practical consequences:**

- **Recruit 18–20, not 12.** Attrition is normal and one drop-out on day thirteen costs you two weeks.
- **Google now checks that testers actually used the app**, not merely that they opted in. Cheap tester-farm gigs that opt in twelve dormant accounts are a real rejection risk and, in the worst case, an account-integrity risk. Do not buy testers on Fiverr.
- **Where to find real ones, in order of quality:** your own contacts (WhatsApp, college and work groups — twelve people you know is genuinely the best option and costs nothing); Android developer communities where you have already participated (r/androiddev, r/AndroidAppTesters, r/betatesting, Android developer Discord and Telegram groups); reciprocal tester-exchange platforms where you test other people's apps for credits. The reciprocal route works and is legitimate, but it fills your test with people who will never use a compass, so their feedback is worthless even though their opt-ins count. Mix: get eight to ten real ones from your network and top up from exchanges.
- **Use the 14 days.** It is free, structured feedback time from a dozen people. Ask them three specific questions — does the heading match a real compass, does the AR view trigger when you expect it to, is the daylight mode readable outdoors — rather than "any feedback?".

### F.2 Launch channels, ranked by whether they are worth your time

**Reddit — worth it, and the single best channel here.** The audience for a deliberately minimal, ad-free Android utility is concentrated on Reddit and nowhere else.
- **r/androidapps** is the primary target. Read the sidebar before posting; communities in this space typically confine self-promotion to a pinned recurring thread, and the rule is enforced. Verify the thread is current and post inside its stated scope.
- **r/Android** is much larger and much stricter; do not attempt a launch post there.
- **r/fossdroid** and **r/degoogle** are receptive to no-ads, minimal-permission apps — but they will ask whether it is open source and whether Firebase is in it. Have an honest answer ready, and consider whether an F-Droid-compatible flavour without Firebase is worth building, because for those communities it is the whole question.
- **Niche audience subreddits are where the real users are:** r/hiking, r/backpacking, r/geocaching, r/ham radio and satellite-dish alignment communities, r/DIY and r/HomeImprovement (for the level), r/astrophotography (for polar alignment). Do not post a launch announcement in these — answer a question where a compass is the answer, and mention what you built. The 90/10 rule is not optional; drive-by promotion gets removed and remembered.
- **Realistic outcome:** a well-received r/androidapps post is worth 200–2,000 installs and, more valuably, 10–40 of your first ratings from people who will actually leave one. Post one thing well rather than five things everywhere.
- **What to post:** lead with the philosophy, not the feature list. Something like "I got tired of compass apps with weather panels and banner ads, so I built one that opens, shows a bearing, and tells you when the magnetometer is unreliable." Attach two screenshots — the rose and the AR view. Include the Play link once, at the bottom. Disclose you are the developer in the first line. Then stay in the thread and answer everything for two days.

**Hacker News (Show HN) — worth one attempt, with modest expectations.** HN's audience skews web and infrastructure; a consumer Android utility rarely gains traction there. But your angle is unusually well suited to it: the interesting story is not "a compass" but the engineering honesty — the coordinate-system remap for upright mode, why the widget deliberately shows no heading, why the AR trigger uses hysteresis and a dwell instead of a bare 45-degree threshold. Write it as a short technical post on the repo or a blog, submit *that* rather than the Play link. Realistic outcome: most likely no traction at all; occasionally a front-page hour worth a few thousand visits and a handful of installs, plus durable search visibility. One shot, Tuesday to Thursday morning US time. Cost: an afternoon writing the post, which you should write regardless.

**Product Hunt — mostly not worth it.** PH's audience is SaaS, AI tools and Mac apps; consumer Android utilities do poorly there, and 40–60% of upvotes on a successful launch come from the maker's own audience, which you do not have. #1 of the day now takes 500–1,200 upvotes. You would spend four weeks building a launch list to place mid-pack. **Skip it**, or list without ceremony and spend the effort on Reddit.

**Android press — low probability, cheap to attempt.** Android Authority, 9to5Google and XDA still cover small apps occasionally; XDA in particular is receptive to ad-free, permission-light, user-respecting apps. Send one short tip email each: three sentences on what it is and why it is different, one line on the technical angle, two screenshots attached, the Play link, and a promo code for the Pro unlock. Do not follow up more than once. **Realistic outcome: no reply, most of the time.** But it costs twenty minutes per outlet and a single pickup is worth more than every other channel combined.

**YouTube reviewers — worth targeting narrowly.** Not the big Android channels; they will not answer. Target small channels that cover minimal and open-source Android apps, plus outdoor and hiking channels where a compass is on-topic. A channel with 5,000 subscribers and a matching audience beats one with 500,000 and a general one. Offer a promo code and ask nothing in return.

**Indie Hackers and Twitter/X — not worth it for this.** IH's audience is founders looking for revenue lessons, not people who need a compass; a build-in-public post there will get sympathy and no installs. Twitter without an existing following is shouting into a void. If you enjoy writing build-in-public updates, keep doing it — just do not count it as distribution.

**ASO is your only compounding channel — treat it as the actual strategy.** Every launch post above is a one-day spike. Search is the only source of installs that grows. Ship the listing well, then revisit the short description and full description at 30, 90 and 180 days against Play Console's own search-term data in the acquisition reports. That data is free, it is real, and it is more useful than any third-party estimate.

### F.3 Ratings and reviews

**The first-fifty problem is real.** Below roughly 50 ratings, Play displays a rating without much confidence weighting and a single one-star review visibly moves your average. Users filter by star rating. Getting from zero to fifty is the hardest stretch you will face, and it is largely won during closed testing and the first two weeks.

- **Ask your 12–20 testers to rate the app on the day it hits production.** Not during testing — closed-test ratings do not appear on the public listing. This is legitimate; asking people who genuinely used the app to leave an honest rating is fine. Offering anything in exchange for a positive rating is not, and violates Play policy.
- **Use the In-App Review API, and use it once.** Google enforces an undocumented per-user quota of a few prompts a year, and best practice is one or two prompts across a user's lifetime. Never attach it to a button — the user may be over quota and the flow will silently not appear, which looks broken.
- **Trigger it at a moment of demonstrated value, not on a timer.** For this app the right trigger is roughly the third or fourth session **and** an accuracy state of HIGH **and** the user has used AR or the level at least once — someone who has seen the app work, on a session where it is working. Fire it when the UI is idle, never mid-interaction and never over the camera view.
- **Reply to every review for the first six months.** Public replies are visible, they measurably lift ratings, and a polite reply to a one-star review about a broken magnetometer on a specific device is the cheapest reputation work available. Play Console notifies you.
- **Watch for the device-specific one-star cluster.** Compass apps attract "doesn't work on my phone" reviews from devices with weak or absent magnetometers. The `uses-feature` declaration for `android.hardware.sensor.compass` with `required="true"` already filters most of these out of the store for incompatible devices — that decision will save you a chunk of your rating.

### F.4 The first thirty days after launch

**Week 1.** Staged rollout at 20% for 48 hours, watching Crashlytics and the Android vitals dashboard, then to 100% if clean. Post to r/androidapps once. Ask your testers for ratings. Reply to everything within a day.

**Week 2.** Send the three press tips. Submit Show HN with the technical write-up. Start answering questions in the niche subreddits — no links, just be useful. Read the Play Console acquisition report's search terms for the first time; there will be little data, but establish the habit.

**Week 3.** Ship v1.0.1 with whatever the first fortnight of real-device reports turned up. A fast first update is itself a positive ranking and credibility signal, and it gives you a legitimate reason to reply to early reviewers that their issue is fixed.

**Week 4.** Review the numbers honestly: installs, day-1 and day-7 retention, uninstall rate, rating count, and the search terms bringing people in. Rewrite the short description against what people actually searched for. Decide, with data rather than hope, whether the v1.2 Pro unlock is worth the two days of billing work — if you are at 300 installs, it is not, and the right move is another month of distribution work first.

**What not to do in the first thirty days:** do not buy installs, do not buy reviews, do not run paid UA on a ₹99 product (your cost per install will exceed your lifetime revenue per install by an order of magnitude), and do not add features. The app's entire proposition is restraint; the fastest way to lose is to answer weak early numbers by making it less minimal.

---

## Pre-launch checklist, in the order things must happen

Dates assume a start of **Friday 5 September 2026** and no slippage. The critical path runs through identity verification into the 14-day closed test; everything else can be done in parallel.

| When | Item | Why this date |
|---|---|---|
| **Sep 5 (today)** | Enable international transactions on your card. Create the Play Console account and pay the US$25. | Card declines are the most common day-one blocker. |
| **Sep 5** | Submit identity and address verification: photo ID (passport / DL / Voter ID / PAN) plus address proof. Check the name and address match the Console profile **character for character** before submitting. | 2–5 business days for individuals; the whole timeline sits behind it. |
| **Sep 5** | Create the Google payments profile and submit PAN. **Begin the BillDesk PA-CB KYC.** | Google's stated deadline for merchant verification is **15 September 2026** — ten days out. New merchants must verify before selling internationally. |
| **Sep 5–8** | Confirm `targetSdk = 36`. (It already is in `app/build.gradle.kts`; `CLAUDE.md` still says 35 and should be corrected.) Verify the release keystore exists and is backed up in two places. | Since **31 August 2026** new apps must target API 36. A lost keystore is unrecoverable. |
| **Sep 6–9** | Write and host the privacy policy at a stable public URL. | Blocks the Data safety form, which blocks release. |
| **Sep 6–9** | Capture the eight screenshots on-device; build the feature graphic and export the 512 × 512 icon. Decide tablet distribution and capture four tablet frames or opt out. | Slowest non-blocking task; start it early. |
| **Sep 8–10** | Verification clears. Create the app in Console. Fill the store listing (title, short and full description from section E), Data safety, content rating, target audience, ads and app-access declarations. | |
| **Sep 9–11** | Build the signed release AAB. Upload to **internal testing**. Install from Play on your own device and check the widget, AR permission flow and daylight mode on a store build with R8 enabled. | R8 and resource shrinking break things that debug builds never show — particularly the reflectively-resolved Glance widget. |
| **Sep 11** | Recruit **18–20 testers**. Create the Google Group or email list, send the opt-in link, confirm every single one has actually opted in and installed. | Twelve must remain opted in continuously; recruit a buffer. |
| **Sep 12** | **Open the closed test.** Day 0 of 14. | |
| **Sep 12–26** | Closed test runs. Mid-window, check the opt-in count and chase anyone who has dropped. Collect feedback, fix what is cheap to fix, but do not restart the track. | Any tester falling below 14 continuous days does not count. |
| **Sep 26–27** | **Apply for production access.** Answer all three sections carefully — describe the test, the app, and your readiness. | |
| **Sep 27 – Oct 4** | Google review. Usually seven days or less. | |
| **Oct 5** | Approval. Configure country availability, pricing (free), and the device catalogue. | |
| **Oct 6** | **Production release at 20% staged rollout.** Watch Crashlytics and Android vitals for 48 hours. | |
| **Oct 8** | Ramp to 100%. Ask testers for public ratings. | |
| **Oct 8** | Post to r/androidapps in the correct thread. Reply to everything for 48 hours. | |
| **Oct 10–14** | Send press tips (Android Authority, 9to5Google, XDA). Submit Show HN with the technical write-up. | |
| **Oct 20** | Ship v1.0.1 with the first fortnight's fixes. | |
| **Nov 5** | Thirty-day review. Read the acquisition search terms. Rewrite the short description. Decide on v1.2 Pro. | |
| **When v1.2 Pro is greenlit** | Integrate Play Billing **9.x** (8 is the floor since 31 Aug 2026). One non-consumable product. `queryPurchasesAsync` on cold start, acknowledge within three days, grandfather by `firstInstallTime`. Update the Data safety form for purchase history. Confirm BillDesk KYC is complete. | |
| **Before any GST decision** | Consult a CA with your India-versus-export revenue split. | The one item that genuinely needs professional advice. |

**Realistic public launch date: mid-October 2026.** If verification or the production-access review runs long, late October. There is no way to compress the 14-day closed test.

---

## Sources

**Play policy and mechanics**
- App testing requirements for new personal developer accounts — https://support.google.com/googleplay/android-developer/answer/14151465
- Target API level requirements for Google Play apps — https://support.google.com/googleplay/android-developer/answer/11926878
- Service fees — https://support.google.com/googleplay/android-developer/answer/112622
- Understanding Google Play's lower service fees — https://support.google.com/googleplay/android-developer/answer/16954621
- Set up your app's prices — https://support.google.com/googleplay/android-developer/answer/6334373
- Add preview assets to showcase your app — https://support.google.com/googleplay/android-developer/answer/9866151
- Provide information for Google Play's Data safety section — https://support.google.com/googleplay/android-developer/answer/10787469
- Understanding user choice billing on Google Play — https://support.google.com/googleplay/android-developer/answer/13821247
- Changes to Google Play's billing requirements for developers serving users in India — https://support.google.com/googleplay/android-developer/answer/13306652
- Verify your developer identity information — https://support.google.com/googleplay/android-developer/answer/10841920
- Developer verification: required documents, India — https://support.google.com/googleplay/android-developer/answer/15633622?co=GENIE.CountryCode%3DIN
- Android developer verification rolling out to all developers (Mar 2026) — https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html
- Expanded billing choice and lower fees on Google Play (Jun 2026) — https://android-developers.googleblog.com/2026/06/play-expanded-billing.html
- Google Play Billing Library version deprecation — https://developer.android.com/google/play/billing/deprecation-faq
- Migrate to Google Play Billing Library 8 — https://developer.android.com/google/play/billing/migrate-gpblv8
- Google Play In-App Reviews API — https://developer.android.com/guide/playcore/in-app-review
- Google Play refund policies — https://support.google.com/googleplay/answer/2479637
- All Google Play refunds to be taken from dev payouts — https://www.gamedeveloper.com/business/all-google-play-refunds-to-be-taken-from-dev-payouts-following-policy-tweaks

**India finance and tax**
- India tax information (Google payments centre) — https://support.google.com/paymentscenter/answer/15152449?hl=en-IN
- Account verification, taxes, and FIRC for sales in India — https://support.google.com/paymentscenter/answer/7421525?hl=en-IN
- Merchant payout schedule — https://support.google.com/paymentscenter/answer/7159355
- Wire transfer payouts — https://support.google.com/googleplay/android-developer/answer/2700656
- Tax rates and value-added tax (VAT) — https://support.google.com/googleplay/android-developer/answer/138000
- Notification 65/2017 – Central Tax — https://gstcouncil.gov.in/node/3992
- Section 393, Income Tax Act 2025: TDS rules and rates — https://www.india-briefing.com/news/section-393-income-tax-act-2025-tds-rules-rates-compliance-guide-44450.html/
- TDS on e-commerce transactions under s.393(1) (formerly 194-O) — https://blog.tdsman.com/2026/07/tds-on-e-commerce-transactions-section-3931-194o/
- Equalisation levy: inception to abolition — https://taxguru.in/income-tax/equalisation-levy-complete-journey-inception-abolition-new-tax-regime.html
- India to end the 2% equalisation levy — https://www.india-briefing.com/news/india-to-abolish-2-percent-equalisation-levy-on-foreign-digital-companies-from-august-1-2024-33736.html/
- GST registration threshold limits 2026 — https://taxgarden.in/blog/gst-registration-turnover-limit-40-lakh-20-lakh-threshold-india-2026
- ITR-4 (Sugam) FAQs, Income Tax Department — https://www.incometax.gov.in/iec/foportal/help/e-filing-itr4-form-sugam-faq
- Play Console developer account cost in India — https://colorleaves.in/blog/google-play-console-developer-account-setup-india/

**Market and competitive data**
- Digital Compass (`com.vincentlee.compass`), AppBrain — https://www.appbrain.com/app/digital-compass/com.vincentlee.compass
- Digital Compass (`com.ktwapps.digitalcompass`), AppBrain — https://www.appbrain.com/app/digital-compass/com.ktwapps.digitalcompass
- Compass: Accurate Compass (`apps.r.compass`), AppBrain — https://www.appbrain.com/app/compass-accurate-compass/apps.r.compass
- Compass (`com.fulminesoftware.compass`), AppBrain — https://www.appbrain.com/app/compass/com.fulminesoftware.compass
- Compass Pro (`com.fulminesoftware.compass.pro`), AppBrain — https://www.appbrain.com/app/compass-pro/com.fulminesoftware.compass.pro
- Compass Maps, AppBrain — https://www.appbrain.com/app/compass-maps/com.directionalcompass.compassmaps
- Compass Steel 3D, Google Play — https://play.google.com/store/apps/details?id=com.simplywerx.compass3d
- Best compass apps for Android, Android Authority — https://www.androidauthority.com/best-compass-apps-android-2-954789/
- Google Play Store statistics 2026 (free vs paid share) — https://www.apptunix.com/blog/google-play-store-statistics/
- Google Play statistics 2026 — https://sqmagazine.co.uk/google-play-store-statistics/

**Monetisation and pricing benchmarks**
- State of Subscription Apps 2026, RevenueCat — https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026
- Google Play IAP pricing by country (2026) — https://pricepush.app/blog/google-play-iap-pricing-by-country
- Google Play service fee 2026: the real math — https://pricepush.app/blog/google-play-subscription-fees-2026-real-math
- Localized pricing for mobile apps — https://pricepush.app/blog/localized-pricing-for-mobile-apps-complete-guide
- Sub-$0.99 pricing rollout across 17 countries, Sensor Tower — https://sensortower.com/blog/google-play-store-app-pricing

**Listing assets and ASO**
- Google Play screenshot requirements 2026 — https://www.applaunchflow.com/blog/google-play-screenshot-requirements-2026
- Google Play screenshot sizes 2026, AppRadar — https://appradar.com/blog/android-app-screenshot-sizes-and-guidelines-for-google-play
- Play Store keyword research in 2026, AppTweak — https://www.apptweak.com/en/aso-blog/play-store-keyword-research
- Google Play short description: 80 characters — https://trysonar.app/blog/google-play-short-description
- App Store and Google Play character limits (2026) — https://www.appstyle.dev/blog/app-store-character-limits/
- Best Google Play screenshot generators 2026 — https://screenhance.com/blog/best-play-store-screenshot-generators-2026
- BrandBird Play Store screenshot generator — https://www.brandbird.app/play-store-screenshot-generator
- AppLaunchpad — https://theapplaunchpad.com/

**Launch and distribution**
- Reddit self-promotion rules by subreddit (2026) — https://www.soar.sh/blog/self-promotion-rules-by-subreddit-database
- The complete guide to Reddit self-promotion rules in 2026 — https://redship.io/blog/reddit-self-promotion-rules
- How to get 12 testers for Google Play closed testing — https://ontest.app/blog/how-to-get-12-testers-for-google-play-closed-testing
- How to launch on Product Hunt 2026 — https://getlaunchlist.com/blog/how-to-launch-on-product-hunt-2026
- Where to launch your app in 2026 — https://screenhance.com/blog/where-to-launch-your-app-2026
- App launch strategy: a 2026 playbook for indie developers — https://www.applaunchflow.com/blog/app-launch-strategy-2026
