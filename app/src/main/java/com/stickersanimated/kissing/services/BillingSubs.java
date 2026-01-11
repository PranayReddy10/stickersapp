package com.stickersanimated.kissing.services;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

public class BillingSubs {
    private static final String TAG = "BillingSubs";

    private final BillingClient billingClient;
    private final Activity activity;
    private final List<String> listProductIds;
    private final CallBackBilling callBackBilling;
    private final CallBackPrice callBackPrice;
    private final CallBackCheck callBackCheck;

    // Private master constructor to consolidate initialization
    private BillingSubs(Activity activity, List<String> listProductIds, CallBackCheck callBackCheck, CallBackBilling callBackBilling, CallBackPrice callBackPrice) {
        this.activity = activity;
        this.listProductIds = listProductIds;
        this.callBackCheck = callBackCheck;
        this.callBackBilling = callBackBilling;
        this.callBackPrice = callBackPrice;

        // This single listener handles all purchase update events.
        PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
            if (callBackBilling != null) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        handleSubscriptionPurchase(purchase);
                    }
                } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                    callBackBilling.onNotPurchase();
                } else {
                    Log.e(TAG, "Purchase Error. Code: " + billingResult.getResponseCode() + " Msg: " + billingResult.getDebugMessage());
                }
            }
        };

        billingClient = BillingClient.newBuilder(activity)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
    }

    // Public constructor for checking subscription status
    public BillingSubs(Activity activity, List<String> listProductIds, CallBackCheck callBackCheck) {
        this(activity, listProductIds, callBackCheck, null, null);
        checkPurchase();
    }

    // Public constructor for making a new subscription purchase
    public BillingSubs(Activity activity, List<String> listProductIds, CallBackBilling callBackBilling) {
        this(activity, listProductIds, null, callBackBilling, null);
    }

    // Public constructor for getting subscription prices
    public BillingSubs(Activity activity, List<String> listProductIds, CallBackPrice callBackPrice) {
        this(activity, listProductIds, null, null, callBackPrice);
        getPrice();
    }

    private void startConnection(Runnable onConnected) {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (onConnected != null) {
                        onConnected.run();
                    }
                } else {
                    Log.e(TAG, "Billing setup failed: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected. Will try to reconnect on next action.");
            }
        });
    }

    private void handleSubscriptionPurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Subscription acknowledged successfully.");
                        if (callBackBilling != null) callBackBilling.onPurchase();
                    } else {
                        Log.e(TAG, "Error acknowledging subscription: " + billingResult.getDebugMessage());
                    }
                });
            } else {
                // The purchase is already acknowledged. Grant entitlement.
                Log.d(TAG, "Subscription was already acknowledged.");
                if (callBackBilling != null) callBackBilling.onPurchase();
            }
        }
    }

    public void purchase(String productId) {
        startConnection(() -> {
            ImmutableList<QueryProductDetailsParams.Product> productList = ImmutableList.of(
                    QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
            );
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(productList).build();

            // Using the correct modern listener structure
            billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
                @Override
                public void onProductDetailsResponse(@NonNull BillingResult billingResult, @NonNull QueryProductDetailsResult queryProductDetailsResult) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
                        if (productDetailsList != null && !productDetailsList.isEmpty()) {
                            ProductDetails productDetails = productDetailsList.get(0);
                            List<ProductDetails.SubscriptionOfferDetails> offerDetailsList = productDetails.getSubscriptionOfferDetails();

                            if (offerDetailsList == null || offerDetailsList.isEmpty()) {
                                Log.e(TAG, "No subscription offers found for product: " + productId);
                                return;
                            }
                            String offerToken = offerDetailsList.get(0).getOfferToken(); // Assuming base plan is the first offer
                            ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = ImmutableList.of(
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                            .setProductDetails(productDetails)
                                            .setOfferToken(offerToken)
                                            .build()
                            );
                            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build();
                            billingClient.launchBillingFlow(activity, billingFlowParams);
                        }
                    } else {
                        Log.e(TAG, "Subscription product not found or error: " + billingResult.getDebugMessage());
                        if (callBackBilling != null) callBackBilling.onNotLogin();
                    }
                }
            });
        });
    }

    public void checkPurchase() {
        startConnection(() -> {
            // 1. Define the parameters for the query, specifying the product type.
            QueryPurchasesParams queryPurchasesParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build();

            // 2. The listener for the asynchronous query.
            billingClient.queryPurchasesAsync(
                    queryPurchasesParams,
                    (billingResult, purchases) -> {
                        // Check if the query was successful.
                        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            Log.e(TAG, "Error querying purchases: " + billingResult.getDebugMessage());
                            if (callBackCheck != null) callBackCheck.onNotPurchase();
                            return;
                        }

                        // 3. Iterate through the list of active purchases returned by the API.
                        for (Purchase purchase : purchases) {
                            // 4. For each purchase, check if it is in the PURCHASED state.
                            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                // 5. Check if the purchase's product ID matches any of the known subscription IDs.
                                //    The 'getProducts()' method replaces the old 'getSkus()'.
                                for (String productId : listProductIds) {
                                    if (purchase.getProducts().contains(productId)) {
                                        Log.d(TAG, "Active subscription found: " + productId);
                                        if (callBackCheck != null) callBackCheck.onPurchase();
                                        return; // An active subscription was found, so we can exit.
                                    }
                                }
                            }
                        }

                        // 6. If the loop completes without finding an active, matching subscription.
                        Log.d(TAG, "No active subscriptions found for the user.");
                        if (callBackCheck != null) callBackCheck.onNotPurchase();
                    }
            );
        });
    }


    public void getPrice() {
        startConnection(() -> {
            ImmutableList.Builder<QueryProductDetailsParams.Product> productListBuilder = ImmutableList.builder();
            for (String productId : listProductIds) {
                productListBuilder.add(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build()
                );
            }
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productListBuilder.build())
                    .build();

            billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                    List<Billing> listBilling = new ArrayList<>();
                    for (ProductDetails productDetails : productDetailsList.getProductDetailsList()) {
                        List<ProductDetails.SubscriptionOfferDetails> offerDetailsList = productDetails.getSubscriptionOfferDetails();
                        if (offerDetailsList != null && !offerDetailsList.isEmpty()) {
                            // Get price from the first (base plan) offer's first pricing phase
                            ProductDetails.SubscriptionOfferDetails basePlan = offerDetailsList.get(0);
                            if (!basePlan.getPricingPhases().getPricingPhaseList().isEmpty()) {
                                String formattedPrice = basePlan.getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
                                String title = productDetails.getTitle();
                                if (title.contains("(")) {
                                    title = title.substring(0, title.indexOf("(")).trim();
                                }
                                listBilling.add(new Billing(
                                        productDetails.getProductId(),
                                        title,
                                        productDetails.getDescription(),
                                        "", // Free trial logic is more complex and omitted here for simplicity
                                        formattedPrice
                                ));
                            }
                        }
                    }
                    if (callBackPrice != null) callBackPrice.onPrice(listBilling);
                } else {
                    Log.e(TAG, "Error getting subscription prices: " + billingResult.getDebugMessage());
                    if (callBackPrice != null) callBackPrice.onNotLogin();
                }
            });
        });
    }
}
