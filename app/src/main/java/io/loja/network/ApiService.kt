package io.loja.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ========== PRICE TAGS ==========
    @GET("web/price-tag/store/{storeId}")
    suspend fun getPriceTags(
        @Path("storeId") storeId: String,
        @Query("startDate") startDate: String
    ): Response<MenuResponse>

    @GET("web/price-tag/store/{storeId}/filter")
    suspend fun getPriceTagFilters(
        @Path("storeId") storeId: String,
        @Query("startDate") startDate: String
    ): Response<FilterResponse>

    @GET("web/price-tag/store/{storeId}/printing/report-full-changes")
    suspend fun getEtiquetasReport(
        @Path("storeId") storeId: String,
        @Query("startDate") startDate: String
    ): Response<ResponseBody>

    @GET("web/price-tag/store/{storeId}/printing")
    suspend fun getPriceTagsByStatus(
        @Path("storeId") storeId: String,
        @Query("status") status: String,
        @Query("department") department: String? = null,
        @Query("startDate") startDate: String? = null
    ): Response<PriceTagsResponse>

    @GET("web/price-tag/store/{storeId}/single-label-printing")
    suspend fun getSingleLabelByEan(
        @Path("storeId") storeId: String,
        @Query("ean") ean: String? = null,
        @Query("description") description: String? = null,
        @Query("sapId") sapId: String? = null,
        @Query("startDate") startDate: String
    ): Response<SingleLabelResponse>

    @POST("web/price-tag/store/{storeId}/printer/{printerId}/tag/{tagId}/send")
    suspend fun sendPriceTagsToPrinter(
        @Path("storeId") storeId: String,
        @Path("printerId") printerId: String,
        @Path("tagId") tagId: String,
        @Body request: SendPriceTagsRequest
    ): Response<Void>

    // ========== PRICE SIGNS ==========
    @GET("web/price-sign/store/{storeId}/filter")
    suspend fun getPriceSignFilters(
        @Path("storeId") storeId: String
    ): Response<PriceSignFilterResponse>

    @GET("web/price-sign/store/{storeId}/printing")
    suspend fun getPriceSigns(
        @Path("storeId") storeId: String,
        @Query("type") type: String,
        @Query("department") department: String? = null,
        @Query("size") size: String? = null,
        @Query("status") status: String,
        @Query("startDate") startDate: String
    ): Response<PriceSignResponse>

    @GET("web/price-sign/store/{storeId}/dashboard")
    suspend fun getPriceSignDashboard(
        @Path("storeId") storeId: String,
        @Query("startDate") startDate: String
    ): Response<ResponseBody>

    @GET("web/price-sign/store/{storeId}/standalone")
    suspend fun getPriceSignStandalone(
        @Path("storeId") storeId: String,
        @Query("type") type: String,
        @Query("ean") ean: String? = null,
        @Query("sapId") sapId: String? = null,
        @Query("description") description: String? = null,
        @Query("startDate") startDate: String
    ): Response<PapeletaStandaloneResponse>

    @POST("web/price-sign/store/{storeId}/send")
    suspend fun sendPriceSigns(
        @Path("storeId") storeId: String,
        @Body request: SendPriceSignRequest
    ): Response<Void>

    @POST("web/price-sign/preview")
    suspend fun previewPriceSign(
        @Body request: PapeletaPrintingData
    ): Response<ResponseBody>

    @GET("web/price-tag/store/{storeId}/printing/report-full-changes")
    suspend fun getPapeletasReport(
        @Path("storeId") storeId: String,
        @Query("startDate") startDate: String
    ): Response<ResponseBody>

    // ========== RECEBIMENTOS ==========
    @GET("web/recebimento/{storeId}")
    suspend fun getRecebimentos(
        @Path("storeId") storeId: String,
        @Query("status") status: String,
        @Query("search") search: String? = null,
        @Query("sort") sort: String = "asc",
        @Query("page") page: Int = 1
    ): Response<RecebimentoResponse>

    // ========== PRINTERS ==========
    @GET("web/priceTag/store/{storeId}/printer")
    suspend fun getPrinters(
        @Path("storeId") storeId: String,
        @Query("orderBy") orderBy: String = "asc",
        @Query("sortBy") sortBy: String = "name"
    ): Response<PrinterResponse>

    // ========== STORE ==========
    @GET("web/store/{storeId}/business-hours")
    suspend fun getBusinessHours(
        @Path("storeId") storeId: String
    ): Response<BusinessHours>

    @GET("web/store/{storeId}/special-business-hours")
    suspend fun getSpecialBusinessHours(
        @Path("storeId") storeId: String,
        @Query("date") date: String
    ): Response<SpecialBusinessHours>

    // ========== LOGIN ==========
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<SessionLoginResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Header("Authorization") token: String
    ): Response<SessionLoginResponse>
}