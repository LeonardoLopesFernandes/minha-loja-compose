package io.loja.network

import com.google.gson.annotations.SerializedName

// ========== PRICE TAG MODELS ==========
data class MenuResponse(val menu: Menu, val page: Page)
data class Menu(val totalChanges: Int)
data class Page(val infoTag: InfoTag, val items: List<DepartmentItem>)
data class InfoTag(val date: DateInfo, val printedTags: Int, val unprintedTags: Int, val totalTags: Int, val nextDayPricingEnabled: Boolean)
data class DateInfo(val day: String, val weekday: String)
data class DepartmentItem(val department: String, val printed: Int, val unprinted: Int)

// ========== FILTERS ==========
data class FilterResponse(val tags: List<TagFilter>, val departments: List<Department>, val status: List<Status>, val printedTags: Int, val unprintedTags: Int)
data class TagFilter(val printerId: String, val tagId: String, val label: String)
data class Department(val id: String, val label: String)
data class Status(val id: String, val label: String)

// ========== PRICE TAGS (ETIQUETAS) ==========
data class PriceTagsResponse(val priceTags: List<PriceTag>)
data class PriceTag(
    val id: String, val sap: String, val department: String, val ean: String,
    val description: String, val startDate: String, val endDate: String,
    val duration: String, val price: String, val movement: String,
    val status: String, val checkbox: Boolean = false, var quantity: Int = 1,
    @SerializedName("_printingData") val printingData: PrintingData? = null
)

data class PrintingData(
    val ean: String, val description: String, val department: String,
    val displayPrice: Double, val price: PriceInfo? = null, val promotionalPrice: PromotionalPriceInfo? = null,
    val quantity: Int, val movementType: String, val unit: String,
    val unitQty: Double, val unitValue: Double, val printUnitValue: Boolean,
    val codSap: String, val takeAndWin: TakeAndWin? = null, val referenceDate: String,
    val template: String? = null, val productName: String? = null,
    val promotionPrice: Double? = null, val takeAndWinQuantity: Int? = null,
    val takeAndWinPrice: Double? = null, val takeAndWinPercent: Int? = null,
    val installments: Installments? = null
)

data class PriceInfo(val value: Double, val discountPercent: Int, val startAt: String, val endAt: String)
data class PromotionalPriceInfo(val value: Double, val discountPercent: Int, val startAt: String, val endAt: String)
data class TakeAndWin(val discountValue: Double, val qty: Int, val totalValue: Double)
data class Installments(
    @SerializedName("installmentQuantity") val installmentQuantity: Int,
    @SerializedName("installmentValue") val installmentValue: Double
)

// ========== SEND TO PRINTER ==========
data class SendPriceTagsRequest(val products: List<PrintingData>)

// ========== PRICE SIGN (PAPELETAS - ESTRUTURA DA API) ==========
data class PriceSignFilterResponse(val size: List<String>, val supplyTypes: List<SupplyType>, val nextDayPricingEnabled: Boolean)
data class SupplyType(val id: String, val label: String, val models: List<SupplyModel>)
data class SupplyModel(val id: String, val label: String)

data class PriceSignResponse(val priceSigns: List<PriceSign>)
data class PriceSign(
    val id: String, val sap: String, val department: String, val ean: String,
    val description: String, val startDate: String, val endDate: String,
    val duration: String, val price: String, val movement: String,
    val status: String, val checkbox: Boolean = false, var quantity: Int = 1,
    @SerializedName("_printingData") val printingData: PapeletaPrintingData? = null
)

// ========== NOVO MODELO EXCLUSIVO PARA PAPELETAS (Baseado no JSON que você mandou) ==========
data class PapeletaPrintingData(
    val template: String? = null,
    val productName: String,
    val price: Double,
    val promotionPrice: Double? = null,
    val takeAndWinQuantity: Int? = null,
    val takeAndWinPrice: Double? = null,
    val takeAndWinPercent: Int? = null,
    val installmentPrice: Double? = null,
    val installmentQuantity: Int? = null,
    val codSap: String,
    val ean: String,
    val referenceDate: String,
    val size: String? = null,
    val quantity: Int? = null,
    val unit: String
)

data class SendPriceSignRequest(val products: List<PapeletaPrintingData>)

// ========== PAPELETA STANDALONE RESPONSE ==========
data class PapeletaStandaloneResponse(
    val items: List<PriceSign> // Usa o PriceSign com o novo printingData
)

// ========== RECEBIMENTOS ==========
data class RecebimentoResponse(val recebimentos: List<Recebimento>, val dateFromGet: String, val page: Int, val pageSize: Int, val totalItems: Int, val totalPages: Int, val qtdRecebimentos: QtdRecebimentos)
data class Recebimento(@SerializedName("_id") val id: String, val viagem_data: String, val codigo_origem: String, val origem: String, val status: String, val placa_veiculo: String, val data_recebimento: String, val qtd_rolls: Int, val qtd_guias: Int, val protocolo: String)
data class QtdRecebimentos(val pendente: Int, val erro: Int)

// ========== PRINTERS ==========
data class PrinterResponse(val printers: List<Printer>, val availablePrinter: List<AvailablePrinter>)
data class Printer(val id: String, val name: String, val store: String, val tags: List<PrinterTag>)
data class PrinterTag(val id: String, val name: String, val orientation: String)
data class AvailablePrinter(val id: String, val name: String, val tag: List<AvailableTag>, val orientations: List<Orientation>)
data class AvailableTag(val id: String, val name: String, val selected: Boolean)
data class Orientation(val name: String, val selected: Boolean)

// ========== SINGLE LABEL ==========
data class SingleLabelResponse(val items: List<SingleLabelItem>)
data class SingleLabelItem(
    val id: String, val sap: String, val department: String, val ean: String,
    val description: String, val startDate: String, val endDate: String,
    val duration: String, val price: String, val movement: String,
    val status: String, val checkbox: Boolean = false, val quantity: Int = 1,
    @SerializedName("_printingData") val printingData: SingleLabelPrintingData? = null
)
data class SingleLabelPrintingData(
    val template: String? = null,
    val ean: String, val description: String, val department: String,
    val displayPrice: Double, val price: PriceInfo, val promotionalPrice: PromotionalPriceInfo? = null,
    val quantity: Int, val movementType: String, val unit: String,
    val unitQty: Double, val unitValue: Double, val printUnitValue: Boolean,
    val codSap: String, val takeAndWin: TakeAndWin, val referenceDate: String,
    val installments: Installments? = null
)

// ========== BUSINESS HOURS ==========
data class BusinessHours(val sunday: DayHours, val monday: DayHours, val tuesday: DayHours, val wednesday: DayHours, val thursday: DayHours, val friday: DayHours, val saturday: DayHours)
data class DayHours(val opening: String, val closing: String, val open: String)

data class SpecialBusinessHours(val date: String, val opening: String? = null, val closing: String? = null, val open: String? = null, val special: Boolean = false)

// ========== DASHBOARD (PRICE SIGN) ==========
data class PriceSignDashboardResponse(
    val page: DashboardPage? = null,
    val total: Int? = null
)
data class DashboardPage(
    val infoTag: DashboardInfo? = null
)
data class DashboardInfo(
    val totalTags: Int? = null,
    val printedTags: Int? = null,
    val unprintedTags: Int? = null
)

// ========== LOGIN MODELS ==========
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val session_token: String? = null,
    val token: String? = null,
    val user: UserInfo? = null,
    val message: String? = null,
    val success: Boolean = false
)

data class SessionLoginResponse(
    @SerializedName("session_token")
    val sessionToken: String? = null,
    val token: String? = null,
    val user: UserInfo? = null,
    val message: String? = null,
    val success: Boolean = false
)

data class UserInfo(
    val email: String,
    val name: String,
    val stores: List<String>,
    val roles: List<String>,
    val grupo: List<String>,
    val perfil: List<String>
)