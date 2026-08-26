package com.stickersanimated.kissing.ads;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;
import com.facebook.ads.AdOptionsView;
import com.inmobi.ads.InMobiNative;
import com.facebook.ads.MediaView;
import com.facebook.ads.NativeAdLayout;
import com.facebook.ads.NativeBannerAd;
import com.google.android.gms.ads.VideoController;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.squareup.picasso.Picasso;
import com.stickersanimated.kissing.R;

import java.util.ArrayList;
import java.util.List;

/** Turns a loaded native ad from any network into a view ready to be added to a container. */
final class NativeAdRenderer {

    private NativeAdRenderer() {
    }

    /** Inflates the AdMob native layout and binds an AdMob native ad to it. */
    static NativeAdView renderAdmob(Context context, NativeAd nativeAd, boolean fullscreen) {
        final NativeAdView adView = (NativeAdView) LayoutInflater.from(context)
                .inflate(fullscreen ? R.layout.ad_unified_fullscreen : R.layout.ad_unified, null);

        adView.setMediaView(adView.findViewById(R.id.ad_media));
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));
        adView.setPriceView(adView.findViewById(R.id.ad_price));
        adView.setStarRatingView(adView.findViewById(R.id.ad_stars));
        adView.setStoreView(adView.findViewById(R.id.ad_store));
        adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));

        ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        if (adView.getMediaView() != null && nativeAd.getMediaContent() != null) {
            adView.getMediaView().setMediaContent(nativeAd.getMediaContent());
        }

        setTextOrHide(adView.getBodyView(), nativeAd.getBody());
        if (nativeAd.getCallToAction() == null) {
            adView.getCallToActionView().setVisibility(View.INVISIBLE);
        } else {
            adView.getCallToActionView().setVisibility(View.VISIBLE);
            ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
        }
        if (nativeAd.getIcon() == null) {
            adView.getIconView().setVisibility(View.GONE);
        } else {
            ((ImageView) adView.getIconView()).setImageDrawable(nativeAd.getIcon().getDrawable());
            adView.getIconView().setVisibility(View.VISIBLE);
        }
        setTextOrHide(adView.getPriceView(), nativeAd.getPrice());
        setTextOrHide(adView.getStoreView(), nativeAd.getStore());
        if (nativeAd.getStarRating() == null) {
            adView.getStarRatingView().setVisibility(View.INVISIBLE);
        } else {
            ((RatingBar) adView.getStarRatingView()).setRating(nativeAd.getStarRating().floatValue());
            adView.getStarRatingView().setVisibility(View.VISIBLE);
        }
        setTextOrHide(adView.getAdvertiserView(), nativeAd.getAdvertiser());

        adView.setNativeAd(nativeAd);

        if (nativeAd.getMediaContent() != null) {
            final VideoController controller = nativeAd.getMediaContent().getVideoController();
            if (controller != null && controller.hasVideoContent()) {
                controller.setVideoLifecycleCallbacks(new VideoController.VideoLifecycleCallbacks() {
                });
            }
        }
        return adView;
    }

    /** The binder shared by every MAX native placement in the app. */
    static MaxNativeAdView createMaxAdView(Context context, boolean fullscreen) {
        final MaxNativeAdViewBinder binder =
                new MaxNativeAdViewBinder.Builder(fullscreen
                        ? R.layout.native_max_ad_view_fullscreen : R.layout.native_max_ad_view)
                        .setTitleTextViewId(R.id.title_text_view)
                        .setBodyTextViewId(R.id.body_text_view)
                        .setAdvertiserTextViewId(R.id.advertiser_textView)
                        .setIconImageViewId(R.id.icon_image_view)
                        .setMediaContentViewGroupId(R.id.media_view_container)
                        .setCallToActionButtonId(R.id.cta_button)
                        .build();
        return new MaxNativeAdView(binder, context);
    }

    /** Builds the Meta Audience Network native banner view and registers it for clicks. */
    static View renderFacebook(Context context, NativeBannerAd nativeBannerAd) {
        nativeBannerAd.unregisterView();

        final NativeAdLayout nativeAdLayout = new NativeAdLayout(context);
        nativeAdLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final View adView = LayoutInflater.from(context)
                .inflate(R.layout.item_native_banner_ad_layout, nativeAdLayout, false);
        nativeAdLayout.addView(adView);

        final RelativeLayout adChoicesContainer = adView.findViewById(R.id.ad_choices_container);
        adChoicesContainer.removeAllViews();
        adChoicesContainer.addView(new AdOptionsView(context, nativeBannerAd, nativeAdLayout), 0);

        final TextView title = adView.findViewById(R.id.native_ad_title);
        final TextView socialContext = adView.findViewById(R.id.native_ad_social_context);
        final TextView sponsoredLabel = adView.findViewById(R.id.native_ad_sponsored_label);
        final MediaView iconView = adView.findViewById(R.id.native_icon_view);
        final Button callToAction = adView.findViewById(R.id.native_ad_call_to_action);

        title.setText(nativeBannerAd.getAdvertiserName());
        socialContext.setText(nativeBannerAd.getAdSocialContext());
        sponsoredLabel.setText(nativeBannerAd.getSponsoredTranslation());
        callToAction.setText(nativeBannerAd.getAdCallToAction());
        callToAction.setVisibility(nativeBannerAd.hasCallToAction() ? View.VISIBLE : View.INVISIBLE);

        final List<View> clickableViews = new ArrayList<>();
        clickableViews.add(title);
        clickableViews.add(callToAction);
        nativeBannerAd.registerViewForInteraction(adView, iconView, clickableViews);

        return nativeAdLayout;
    }

    /**
     * Vungle native. The SDK hands over the parts and fills the media and icon views
     * itself once the container is registered, so the layout only has to provide them.
     */
    static View renderVungle(Context context, com.vungle.ads.NativeAd nativeAd,
                             boolean fullscreen) {
        final View view = LayoutInflater.from(context).inflate(fullscreen
                ? R.layout.network_native_fullscreen : R.layout.item_network_native_ads, null);
        final FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout_native_root);
        final FrameLayout media = (FrameLayout) view.findViewById(R.id.frame_layout_native_media);
        final ImageView icon = (ImageView) view.findViewById(R.id.image_view_native_icon);
        final TextView title = (TextView) view.findViewById(R.id.text_view_native_title);
        final TextView body = (TextView) view.findViewById(R.id.text_view_native_body);
        final TextView cta = (TextView) view.findViewById(R.id.text_view_native_cta);
        final TextView sponsored = (TextView) view.findViewById(R.id.text_view_native_sponsored);

        title.setText(nativeAd.getAdTitle());
        setTextOrHide(body, nativeAd.getAdBodyText());
        cta.setText(nativeAd.getAdCallToActionText());
        cta.setVisibility(nativeAd.hasCallToAction() ? View.VISIBLE : View.INVISIBLE);
        if (nativeAd.getAdSponsoredText() != null && !nativeAd.getAdSponsoredText().isEmpty()) {
            sponsored.setText(nativeAd.getAdSponsoredText());
        }

        final com.vungle.ads.internal.ui.view.MediaView mediaView =
                new com.vungle.ads.internal.ui.view.MediaView(context);
        media.removeAllViews();
        media.addView(mediaView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The privacy button has to stay reachable, so keep it off the media itself.
        nativeAd.setAdOptionsPosition(com.vungle.ads.NativeAd.TOP_RIGHT);

        final List<View> clickable = new ArrayList<>();
        clickable.add(cta);
        clickable.add(title);
        clickable.add(mediaView);
        nativeAd.registerViewForInteraction(root, mediaView, icon, clickable);
        return view;
    }

    /**
     * InMobi native. Unlike the others it returns the creative as a view sized to a
     * width, and the click has to be reported by hand.
     */
    static View renderInMobi(Context context, InMobiNative nativeAd, boolean fullscreen, int width) {
        final View view = LayoutInflater.from(context).inflate(fullscreen
                ? R.layout.network_native_fullscreen : R.layout.item_network_native_ads, null);
        final FrameLayout media = (FrameLayout) view.findViewById(R.id.frame_layout_native_media);
        final ImageView icon = (ImageView) view.findViewById(R.id.image_view_native_icon);
        final TextView title = (TextView) view.findViewById(R.id.text_view_native_title);
        final TextView body = (TextView) view.findViewById(R.id.text_view_native_body);
        final TextView cta = (TextView) view.findViewById(R.id.text_view_native_cta);

        title.setText(inmobiText(nativeAd, "getAdTitle", ""));
        setTextOrHide(body, inmobiText(nativeAd, "getAdDescription", ""));
        setTextOrHide(cta, inmobiText(nativeAd, "getAdCtaText", "OPEN"));

        final String iconUrl = inmobiText(nativeAd, "getAdIconUrl", "");
        if (iconUrl != null && !iconUrl.trim().isEmpty()) {
            Picasso.get().load(iconUrl.trim()).into(icon);
        } else {
            icon.setVisibility(View.GONE);
        }

        final View creative = nativeAd.getPrimaryViewOfWidth(context, null, media, width);
        media.removeAllViews();
        if (creative != null) {
            media.addView(creative, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        final View.OnClickListener open = v -> nativeAd.reportAdClickAndOpenLandingPage();
        cta.setOnClickListener(open);
        title.setOnClickListener(open);
        media.setOnClickListener(open);
        return view;
    }

    /**
     * One of InMobi's text getters, or the fallback when this SDK build does not have
     * it. The names have come and gone across InMobi releases - getAdCtaText is absent
     * from some - and a missing getter is not worth failing the whole ad over.
     */
    private static String inmobiText(InMobiNative nativeAd, String getter, String fallback) {
        try {
            final Object value = InMobiNative.class.getMethod(getter).invoke(nativeAd);
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return (String) value;
            }
        } catch (Throwable ignored) {
            // Not in this SDK build, or it threw: use the fallback.
        }
        return fallback;
    }

    private static void setTextOrHide(View view, CharSequence text) {
        if (view == null) {
            return;
        }
        if (text == null) {
            view.setVisibility(View.INVISIBLE);
        } else {
            view.setVisibility(View.VISIBLE);
            ((TextView) view).setText(text);
        }
    }
}
