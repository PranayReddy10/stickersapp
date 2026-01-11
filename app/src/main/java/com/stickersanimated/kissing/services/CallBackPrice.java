package com.stickersanimated.kissing.services;

import java.util.List;

public interface CallBackPrice {
    void onNotLogin();
    void onPrice(List<Billing> listBilling);
}