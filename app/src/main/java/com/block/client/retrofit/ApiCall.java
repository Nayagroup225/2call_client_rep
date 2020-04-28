package com.block.client.retrofit;

import com.block.client.model.BaseResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiCall {
    private static APIService service;

    public static ApiCall getInstance() {
        if (service == null) {
            service = RestClient.getClient();
        }
        return new ApiCall();
    }

    public void addOrGetCallId(String deviceId, String phoneNumber, String adminIdentify, IApiCallback<BaseResponse> iApiCallback) {
        Call<BaseResponse> call = service.addOrGetCallId(deviceId, phoneNumber, adminIdentify);
        call.enqueue(new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                iApiCallback.onSuccess("check", response);
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                iApiCallback.onFailure("" + t.getMessage());
            }
        });
    }
}
