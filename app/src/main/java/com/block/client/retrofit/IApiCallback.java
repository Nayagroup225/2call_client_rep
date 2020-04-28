package com.block.client.retrofit;

import retrofit2.Response;

/**
 * Interface is used for common purpose in Application.
 *
 * @author and15031989
 */
public interface IApiCallback<T> {
    /**
     * Method for getting the type and data.
     *
     * @param response Actual data
     */
    void onSuccess(String type, Response<T> response);

    /**
     * Failure Reason
     * @param data
     */
    void onFailure(Object data);

}
