package io.loja.network.models

import com.google.gson.annotations.SerializedName

// ========== RESUMO POR DEPARTAMENTO ==========
data class PriceTagSummaryResponse(
    @SerializedName("menu") val menu: PriceTagMenu,
    @SerializedName("page") val page: PriceTagPage
)

data class PriceTagMenu(
    @SerializedName("totalChanges") val totalChanges: Int
)

data class PriceTagPage(
    @SerializedName("infoTag") val infoTag: PriceTagInfo,
    @SerializedName("items") val items: List<PriceTagDepartment>
)

data class PriceTagInfo(
    @SerializedName("date") val date: PriceTagDate,
    @SerializedName("printedTags") val printedTags: Int,
    @SerializedName("unprintedTags") val unprintedTags: Int,
    @SerializedName("totalTags") val totalTags: Int,
    @SerializedName("nextDayPricingEnabled") val nextDayPricingEnabled: Boolean
)

data class PriceTagDate(
    @SerializedName("day") val day: String,
    @SerializedName("weekday") val weekday: String
)

data class PriceTagDepartment(
    @SerializedName("department") val department: String,
    @SerializedName("printed") val printed: Int,
    @SerializedName("unprinted") val unprinted: Int
)

// ========== FILTROS ==========
data class PriceTagFilterResponse(
    @SerializedName("tags") val tags: List<PriceTagPrinter>,
    @SerializedName("departments") val departments: List<PriceTagFilterDepartment>,
    @SerializedName("status") val status: List<PriceTagFilterStatus>,
    @SerializedName("printedTags") val printedTags: Int,
    @SerializedName("unprintedTags") val unprintedTags: Int
)

data class PriceTagPrinter(
    @SerializedName("printerId") val printerId: String,
    @SerializedName("tagId") val tagId: String,
    @SerializedName("label") val label: String
)

data class PriceTagFilterDepartment(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String
)

data class PriceTagFilterStatus(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String
)

// ========== LISTA DE ETIQUETAS ==========
data class PriceTagListResponse(
    @SerializedName("priceTags") val priceTags: List<PriceTagItem>
)

data class PriceTagItem(
    @SerializedName("id") val id: String,
    @SerializedName("sap") val sap: String,
    @SerializedName("department") val department: String,
    @SerializedName("ean") val ean: String,
    @SerializedName("description") val description: String,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("endDate") val endDate: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("price") val price: String,
    @SerializedName("movement") val movement: String,
    @SerializedName("status") val status: String,
    @SerializedName("checkbox") val checkbox: Boolean = false,
    @SerializedName("quantity") val quantity: Int = 1,
    @SerializedName("_printingData") val printingData: PriceTagPrintingData?
)

data class PriceTagPrintingData(
    @SerializedName("template") val template: String? = null,
    @SerializedName("ean") val ean: String,
    @SerializedName("description") val description: String,
    @SerializedName("department") val department: String,
    @SerializedName("displayPrice") val displayPrice: Double,
    @SerializedName("price") val price: PriceTagPrice,
    @SerializedName("promotionalPrice") val promotionalPrice: PriceTagPromotionalPrice? = null,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("movementType") val movementType: String,
    @SerializedName("unit") val unit: String,
    @SerializedName("unitQty") val unitQty: Double,
    @SerializedName("unitValue") val unitValue: Double,
    @SerializedName("printUnitValue") val printUnitValue: Boolean,
    @SerializedName("codSap") val codSap: String,
    @SerializedName("takeAndWin") val takeAndWin: PriceTagTakeAndWin? = null,
    @SerializedName("installments") val installments: PriceTagInstallments? = null,
    @SerializedName("referenceDate") val referenceDate: String
)

data class PriceTagPrice(
    @SerializedName("value") val value: Double,
    @SerializedName("discountPercent") val discountPercent: Int,
    @SerializedName("startAt") val startAt: String,
    @SerializedName("endAt") val endAt: String
)

data class PriceTagPromotionalPrice(
    @SerializedName("value") val value: Double,
    @SerializedName("discountPercent") val discountPercent: Int,
    @SerializedName("startAt") val startAt: String,
    @SerializedName("endAt") val endAt: String
)

data class PriceTagTakeAndWin(
    @SerializedName("discountValue") val discountValue: Double,
    @SerializedName("qty") val qty: Int,
    @SerializedName("totalValue") val totalValue: Double
)

data class PriceTagInstallments(
    @SerializedName("installmentQuantity") val installmentQuantity: Int,
    @SerializedName("installmentValue") val installmentValue: Double
)

// ========== RESPOSTA DE ENVIO ==========
data class PriceTagSendResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null
)

// ========== PRODUTO (saldo) ==========
data class ProductStoreResponse(
    @SerializedName("stock") val stock: Double = 0.0
)