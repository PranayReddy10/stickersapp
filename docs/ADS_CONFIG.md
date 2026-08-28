# Ad networks and backend settings

The app no longer serves a single hard-coded network per format. Every format now runs a
**waterfall**: it asks the first network in the list, and if that network errors out or does
not answer within a timeout, it moves on to the next one. Only when every network in the
list has failed does the slot stay empty — and for interstitials the user is never blocked,
navigation happens with or without an ad.

## Networks

| Value in the panel | SDK | Banner | Native | Interstitial | Rewarded |
|---|---|---|---|---|---|
| `ADMOB` | Google Mobile Ads | ✅ | ✅ | ✅ | ✅ |
| `MAX` | AppLovin MAX mediation | ✅ | ✅ | ✅ | ✅ |
| `APPLOVIN` | AppLovin direct | ✅ | — | ✅ | ✅ |
| `FACEBOOK` | Meta Audience Network | ✅ | ✅ | ✅ | ✅ |
| `UNITY` | Unity Ads | ✅ | — | ✅ | ✅ |
| `VUNGLE` | Liftoff Monetize (Vungle) | ✅ | ✅ | ✅ | ✅ |
| `INMOBI` | InMobi | ✅ | ✅ | ✅ | ✅ |

Aliases accepted for each: `GOOGLE`/`ADMANAGER`, `APPLOVIN_MAX`, `FB`/`META`/`FAN`,
`UNITYADS`, `LIFTOFF`.

Native ads are served by AdMob, AppLovin MAX, Meta, Vungle and InMobi. AppLovin's
direct integration and Unity Ads have no native format for publishers to request, so
they are skipped for that placement.

Networks that cannot serve a format, or that have no unit id configured, are skipped
automatically, so it is safe to leave a network out.

### When AdMob or Meta is unavailable

Native ads then come from `MAX`, `VUNGLE` and `INMOBI` only — those are the remaining
networks with a native format. Each needs two things filled in on the Ads page, and an ad
never appears if either is missing:

| Network | Unit id key | Account level key |
|---|---|---|
| `MAX` | `ADMIN_NATIVE_MAX_ID` (a MAX **native** ad unit, not an AdMob one) | AppLovin SDK key, shipped in the app |
| `VUNGLE` | `ADMIN_NATIVE_VUNGLE_ID` | `ADMIN_VUNGLE_APP_ID` |
| `INMOBI` | `ADMIN_NATIVE_INMOBI_ID` (numeric placement) | `ADMIN_INMOBI_ACCOUNT_ID` |

Set `ADMIN_NATIVE_ORDER` to the networks that can actually serve (for example
`MAX,VUNGLE,INMOBI`) and `ADMIN_AD_FALLBACK` to `FALSE`, so a slot does not spend a
timeout on a network that cannot fill before reaching one that can.

Every network skipped for a format is logged with the field it is waiting on:

```
AdsConfig: VUNGLE skipped for NATIVE: ADMIN_VUNGLE_APP_ID is empty
NativeAds: Native waterfall [MAX, INMOBI], 10000ms per network
```

Unity, Vungle and InMobi are started from credentials the panel sends, which are not
present when the process starts. They are started again as soon as the settings call
returns, and an ad request that arrives while an SDK is still coming up waits for it
rather than being counted as a failure.

## Settings the panel can send

The settings endpoint returns `name` / `value` pairs. **Any** name starting with `ADMIN_` is
now stored, so new keys can be added on the backend without shipping a new APK.

`<FORMAT>` is one of `BANNER`, `NATIVE`, `INTERSTITIAL`, `REWARDED`.

| Key | Meaning |
|---|---|
| `ADMIN_<FORMAT>_TYPE` | Primary network, or `FALSE` to switch the format off. A comma separated list is also accepted. The rewarded key keeps its historic name `ADMIN_REWARDED_AD_TYPE`. |
| `ADMIN_<FORMAT>_ORDER` | Explicit waterfall, e.g. `ADMOB,MAX,FACEBOOK,UNITY`. Wins over `_TYPE`. |
| `ADMIN_<FORMAT>_ADMOB_ID` | AdMob unit id. |
| `ADMIN_<FORMAT>_MAX_ID` | AppLovin MAX unit id. Falls back to the AdMob key, which is where older panels stored it. |
| `ADMIN_<FORMAT>_APPLOVIN_ID` | AppLovin direct zone id (optional — AppLovin direct works off the SDK key alone). |
| `ADMIN_<FORMAT>_FACEBOOK_ID` | Meta Audience Network placement id. For native, `ADMIN_NATIVE_BANNER_FACEBOOK_ID` is also accepted. |
| `ADMIN_<FORMAT>_UNITY_ID` | Unity Ads placement id, e.g. `Interstitial_Android`. |
| `ADMIN_<FORMAT>_VUNGLE_ID` | Liftoff Monetize (Vungle) placement id. |
| `ADMIN_<FORMAT>_INMOBI_ID` | InMobi placement id. Numeric — a non-numeric value makes the app skip InMobi for that format. |
| `ADMIN_UNITY_GAME_ID` | Unity Ads game id. Unity is skipped entirely while this is empty. |
| `ADMIN_VUNGLE_APP_ID` | Liftoff Monetize app id. Vungle is skipped entirely while this is empty. |
| `ADMIN_INMOBI_ACCOUNT_ID` | InMobi account id. InMobi is skipped entirely while this is empty. |
| `ADMIN_AD_FALLBACK` | `TRUE` (default) appends every other configured network after the ones listed. `FALSE` uses only `_ORDER` / `_TYPE`. |
| `ADMIN_AD_TIMEOUT` | Seconds to wait for one network before moving on. Default `10`, clamped to 3–60. |
| `ADMIN_INTERSTITIAL_CLICKS` | Clicks between two interstitials (unchanged). |
| `ADMIN_NATIVE_LINES` | Packs between two in-feed native ads. Set it to `3` or `4` for an ad every few packs on the home screen. A missing or invalid value falls back to 3. |
| `ADMIN_REELS_NATIVE_LINES` | Reels between two native ads, both in the reels card feed and in the full screen player. A missing or invalid value falls back to 3. |

### Example

```
ADMIN_BANNER_ORDER        = ADMOB,MAX,FACEBOOK,UNITY,VUNGLE,INMOBI
ADMIN_BANNER_ADMOB_ID     = ca-app-pub-xxx/1111111111
ADMIN_BANNER_MAX_ID       = 0123456789abcdef
ADMIN_BANNER_FACEBOOK_ID  = 1234567890_1234567890
ADMIN_BANNER_UNITY_ID     = Banner_Android
ADMIN_UNITY_GAME_ID       = 5123456
ADMIN_AD_TIMEOUT          = 8
```

With this configuration a banner request goes to AdMob first; if AdMob returns no fill (or
stays silent for 8 seconds) the app asks MAX, then Meta, then Unity.


## Where each format appears in the app

| Placement | Format | Controlled by |
|---|---|---|
| Home / category / search / popular / user lists, every N packs | Native | `ADMIN_NATIVE_*` + `ADMIN_NATIVE_LINES` |
| Pack details screen, under the pack | Native | `ADMIN_NATIVE_*` |
| Pack details, category and search screens, bottom bar | Banner | `ADMIN_BANNER_*` |
| Opening a pack from a list | Interstitial | `ADMIN_INTERSTITIAL_*` + `ADMIN_INTERSTITIAL_CLICKS` |
| Reels card feed and full screen player, every N reels | Native | `ADMIN_NATIVE_*` + `ADMIN_REELS_NATIVE_LINES` |
| Unlocking a **premium** pack (watch an ad or subscribe) | Rewarded | `ADMIN_REWARDED_*` |

Adding a free pack to WhatsApp, Telegram or Signal shows no ad of its own. A premium pack
keeps its existing unlock dialog - watch a rewarded video or subscribe - and that video now
runs the whole waterfall, so one network failing to fill no longer blocks the unlock. If
rewarded ads are switched off entirely, or every network comes back empty, the user is not
left stuck behind an ad that can never load.

### Backwards compatibility

An install that only has the old keys (`ADMIN_BANNER_TYPE`, `ADMIN_BANNER_ADMOB_ID`, …)
behaves exactly as before, with one difference: the network named in `_TYPE` is now tried
first and the other configured networks act as its backup instead of the slot going empty.
Set `ADMIN_AD_FALLBACK=FALSE` if you want the strict old single-network behaviour.

## Android 16 (API 36)

`compileSdk` and `targetSdk` are 36. The behaviour changes that needed handling:

- **Edge to edge is mandatory.** `windowOptOutEdgeToEdgeEnforcement` is ignored on API 36, so
  `utils/EdgeToEdgeHelper` applies the system bar and keyboard insets to any screen whose
  layout does not already use `fitsSystemWindows`, and repaints the status/navigation bar
  strips with the theme colours the platform now ignores.
- **Predictive back is on by default** for apps targeting 36, which would stop the existing
  `onBackPressed()` overrides from running. The manifest sets
  `android:enableOnBackInvokedCallback="false"` to keep the current back behaviour.
- **16 KB memory pages.** `packaging.jniLibs.useLegacyPackaging = false` keeps the native
  libraries uncompressed and page aligned; the one bundled `.so` (`libconceal.so`) is
  already built with 16 KB alignment.
