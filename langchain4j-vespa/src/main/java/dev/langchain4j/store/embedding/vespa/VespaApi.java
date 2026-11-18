package dev.langchain4j.store.embedding.vespa;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

interface VespaApi {
    @GET("search/")
    Call<QueryResponse> search(
            @Query("yql") String yql,
            @Query("hits") int hits,
            @Query("ranking") String ranking,
            @Query("input.query(q)") String queryVector,
            @Query("input.query(threshold)") double threshold);

    @POST("document/v1/{ns}/{docType}/docid/{id}")
    Call<Void> putDocument(
            @Path("ns") String namespace,
            @Path("docType") String documentType,
            @Path("id") String id,
            @Body RequestBody document);

    @DELETE("document/v1/{ns}/{docType}/docid?selection=true")
    Call<DeleteResponse> deleteAll(
            @Path("ns") String namespace, @Path("docType") String documentType, @Query("cluster") String clusterName);
}
