package com.derrick.googlemap.network;

import com.derrick.googlemap.models.Direction;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiInterface {
    @GET("directions/json")
    Call<Direction> getTopRatedMovies(@Query("origin") String origin,@Query("destination") String destination,@Query("key") String apiKey);

}
