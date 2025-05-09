package com.example.myapplication;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @GET("api/access-request/verify-infor")
    Call<RegistrationResponse> verifyCccd(@Query("cccd") String cccd);

}