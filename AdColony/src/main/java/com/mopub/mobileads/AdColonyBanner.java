package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adcolony.sdk.AdColony;
import com.adcolony.sdk.AdColonyAdSize;
import com.adcolony.sdk.AdColonyAdView;
import com.adcolony.sdk.AdColonyAdViewListener;
import com.adcolony.sdk.AdColonyZone;
import com.mopub.common.LifecycleListener;
import com.mopub.common.logging.MoPubLog;
import com.mopub.common.util.Json;

import java.util.Map;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.WILL_LEAVE_APPLICATION;

public class AdColonyBanner extends BaseAd {

    private static final String ADAPTER_NAME = AdColonyBanner.class.getSimpleName();

    private AdColonyAdViewListener mAdColonyBannerListener;
    private final Handler mHandler;

    @NonNull
    private AdColonyAdapterConfiguration mAdColonyAdapterConfiguration;
    private AdColonyAdView mAdColonyAdView;

    @NonNull
    private String mZoneId = AdColonyAdapterConfiguration.DEFAULT_ZONE_ID;

    @NonNull
    public String getAdNetworkId() {
        return mZoneId;
    }

    @Override
    protected boolean checkAndInitializeSdk(@NonNull Activity launcherActivity, @NonNull AdData adData) {
        return false;
    }

    public AdColonyBanner() {
        mHandler = new Handler();
        mAdColonyAdapterConfiguration = new AdColonyAdapterConfiguration();
    }

    @Override
    protected void load(@NonNull final Context context,
                        @NonNull final AdData adData) {
        if (!(context instanceof Activity)) {
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR.getIntCode(), MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Aborting Ad Colony banner load request as the context calling it is not an instance of Activity.");
            if (mLoadListener != null) {
                mLoadListener.onAdLoadFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            }
            return;
        }

        AdColonyAdSize adSize = getAdSize(adData);
        if (adSize == null) {
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR.getIntCode(), MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Aborting Ad Colony banner load request as the adSize requested is invalid");
            if (mLoadListener != null) {
                mLoadListener.onAdLoadFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            }
            return;
        }
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Requested ad size is: w: " + adSize.getWidth() + " h: " + adSize.getHeight());

        final Map<String, String> extras = adData.getExtras();
        String clientOptions = extras.get(AdColonyAdapterConfiguration.CLIENT_OPTIONS_KEY);
        if (clientOptions == null)
            clientOptions = "";

        final String appId = AdColonyAdapterConfiguration.getAdColonyParameter(AdColonyAdapterConfiguration.APP_ID_KEY, extras);
        final String zoneId = AdColonyAdapterConfiguration.getAdColonyParameter(AdColonyAdapterConfiguration.ZONE_ID_KEY, extras);

        String[] allZoneIds;
        String allZoneIdsString = AdColonyAdapterConfiguration.getAdColonyParameter(AdColonyAdapterConfiguration.ALL_ZONE_IDS_KEY, extras);
        if (allZoneIdsString != null) {
            allZoneIds = Json.jsonArrayToStringArray(allZoneIdsString);
        } else {
            allZoneIds = null;
        }

        if (appId == null) {
            abortRequestForIncorrectParameter(AdColonyAdapterConfiguration.APP_ID_KEY);
            return;
        }

        if (zoneId == null || allZoneIds == null || allZoneIds.length == 0) {
            abortRequestForIncorrectParameter(AdColonyAdapterConfiguration.ZONE_ID_KEY);
            return;
        }

        mZoneId = zoneId;

        mAdColonyAdapterConfiguration.setCachedInitializationParameters(context, extras);
        mAdColonyBannerListener = getAdColonyBannerListener();

        AdColonyAdapterConfiguration.checkAndConfigureAdColonyIfNecessary(context, clientOptions, appId, allZoneIds);
        AdColony.requestAdView(zoneId, mAdColonyBannerListener, adSize);
        MoPubLog.log(getAdNetworkId(), LOAD_ATTEMPTED, ADAPTER_NAME);
    }

    @Nullable
    @Override
    protected View getAdView() {
        return mAdColonyAdView;
    }

    private void abortRequestForIncorrectParameter(String parameterName) {
        AdColonyAdapterConfiguration.logAndFail("banner request", parameterName);
        if (mLoadListener != null) {
            mLoadListener.onAdLoadFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
        }
    }

    @Override
    protected void onInvalidate() {
        if (mAdColonyAdView != null) {
            mAdColonyAdView.destroy();
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Banner destroyed");
            mAdColonyAdView = null;
        }

        mAdColonyBannerListener = null;
    }

    @Nullable
    @Override
    protected LifecycleListener getLifecycleListener() {
        return null;
    }

    private AdColonyAdViewListener getAdColonyBannerListener() {
        if (mAdColonyBannerListener != null) {
            return mAdColonyBannerListener;
        } else {
            return new AdColonyAdViewListener() {

                @Override
                public void onRequestFilled(final AdColonyAdView adColonyAdView) {
                    mAdColonyAdView = adColonyAdView;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mLoadListener != null) {
                                mLoadListener.onAdLoaded();
                            }
                            MoPubLog.log(getAdNetworkId(), LOAD_SUCCESS, ADAPTER_NAME);
                        }
                    });
                }

                @Override
                public void onRequestNotFilled(AdColonyZone zone) {
                    super.onRequestNotFilled(zone);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mLoadListener != null) {
                                mLoadListener.onAdLoadFailed(MoPubErrorCode.NETWORK_NO_FILL);
                            }
                            MoPubLog.log(getAdNetworkId(), LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                                    MoPubErrorCode.NETWORK_NO_FILL);
                        }
                    });
                }

                @Override
                public void onClicked(AdColonyAdView ad) {
                    super.onClicked(ad);
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdClicked();
                    }
                    MoPubLog.log(getAdNetworkId(), CLICKED, ADAPTER_NAME);
                }

                @Override
                public void onLeftApplication(AdColonyAdView ad) {
                    super.onLeftApplication(ad);
                    MoPubLog.log(getAdNetworkId(), WILL_LEAVE_APPLICATION, ADAPTER_NAME);
                }

                @Override
                public void onOpened(AdColonyAdView ad) {
                    super.onOpened(ad);
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Banner opened fullscreen");
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdExpanded();
                    }
                }

                @Override
                public void onClosed(AdColonyAdView ad) {
                    super.onClosed(ad);
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Banner closed fullscreen");
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdCollapsed();
                    }
                }
            };
        }
    }

    @Nullable
    private AdColonyAdSize getAdSize(@NonNull final AdData adData) {
        final Integer width = adData.getAdWidth();
        final Integer height = adData.getAdHeight();

        if (width != null && height != null) {

            if (height >= AdColonyAdSize.SKYSCRAPER.getHeight() && width >= AdColonyAdSize.SKYSCRAPER.getWidth()) {
                return AdColonyAdSize.SKYSCRAPER;
            } else if (height >= AdColonyAdSize.MEDIUM_RECTANGLE.getHeight() && width >= AdColonyAdSize.MEDIUM_RECTANGLE.getWidth()) {
                return AdColonyAdSize.MEDIUM_RECTANGLE;
            } else if (height >= AdColonyAdSize.LEADERBOARD.getHeight() && width >= AdColonyAdSize.LEADERBOARD.getWidth()) {
                return AdColonyAdSize.LEADERBOARD;
            } else if (height >= AdColonyAdSize.BANNER.getHeight() && width >= AdColonyAdSize.BANNER.getWidth()) {
                return AdColonyAdSize.BANNER;
            } else {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "Requested ad size doesn't fit to any banner size supported by AdColony, will abort request.");
                return null;
            }
        }

        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Requested ad size is invalid, will abort request.");
        return null;
    }
}
