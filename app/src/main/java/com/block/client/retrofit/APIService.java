package com.block.client.retrofit;

import com.block.client.model.BaseResponse;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

interface APIService {

    @FormUrlEncoded
    @POST("getCallId")
    Call<BaseResponse> addOrGetCallId(@Field("device_id") String deviceId,
                                      @Field("phone_number") String phoneNumber,
                                      @Field("admin_identify") String adminIdentify);

}
