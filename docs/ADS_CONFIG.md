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

Aliases accepted for each: `GOOGLE`/`ADMANAGER`, `APPLOVIN_MAX`, `FB`/`META`/`FAN`,
`UNITYADS`.

Networks that cannot serve a format, or that have no unit id configured, are skipped
automatically, so it is safe to leave a network out.

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
| `ADMIN_UNITY_GAME_ID` | Unity Ads game id. Unity is skipped entirely while this is empty. |
| `ADMIN_AD_FALLBACK` | `TRUE` (default) appends every other configured network after the ones listed. `FALSE` uses only `_ORDER` / `_TYPE`. |
| `ADMIN_AD_TIMEOUT` | Seconds to wait for one network before moving on. Default `10`, clamped to 3–60. |
| `ADMIN_INTERSTITIAL_CLICKS` | Clicks between two interstitials (unchanged). |
| `ADMIN_NATIVE_LINES` | List rows between two in-feed native ads (unchanged). |

### Example

```
ADMIN_BANNER_ORDER        = ADMOB,MAX,FACEBOOK,UNITY
ADMIN_BANNER_ADMOB_ID     = ca-app-pub-xxx/1111111111
ADMIN_BANNER_MAX_ID       = 0123456789abcdef
ADMIN_BANNER_FACEBOOK_ID  = 1234567890_1234567890
ADMIN_BANNER_UNITY_ID     = Banner_Android
ADMIN_UNITY_GAME_ID       = 5123456
ADMIN_AD_TIMEOUT          = 8
```

With this configuration a banner request goes to AdMob first; if AdMob returns no fill (or
stays silent for 8 seconds) the app asks MAX, then Meta, then Unity.

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
