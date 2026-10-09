package io.loja.utils

object Constants {
    const val DEFAULT_STORE = "L291"
    
    const val RECEBIMENTO_PENDENTE = "pendente"
    const val RECEBIMENTO_RECEBIDO = "recebido"
    const val RECEBIMENTO_ANOMALIA = "anomalia"
    const val RECEBIMENTO_ERRO = "erro"
    
    const val STATUS_IMPRESSAS = "IMPRESSAS"
    const val STATUS_NAO_IMPRESSAS = "NAO_IMPRESSAS"
    const val STATUS_ALL = "IMPRESSAS,NAO_IMPRESSAS"
    
    const val PRINTER_LASER_1 = "LASER_1"
    const val PRINTER_ZEBRA_1 = "ZEBRA_1"
    const val PRINTER_ZEBRA_2 = "ZEBRA_2"
    
    const val TAG_GONDOLA = "GONDOLA"
    const val TAG_ADESIVA = "ADESIVA"
    const val TAG_CARTAO = "CARTAO"
    
    const val SIGN_TYPE_PAPELETA_COMUM = "PAPELETA_COMUM"
    const val SIGN_TYPE_PAPELETA_PROMOCIONAL = "PAPELETA_PROMOCIONAL"
    const val TIPO_COMUM = "PAPELETA_COMUM"
    const val TIPO_PROMOCIONAL = "PAPELETA_PROMOCIONAL"
    const val SIGN_TYPE_PAPELETA_PROMOCIONAL_MODELO = "PAPELETA_PROMOCIONAL_MODELO"
    const val SIGN_TEMPLATE_MODELO = "leve_ganhe_total"
    const val SIGN_TEMPLATE_MODELO_EDITAVEL = "Modelo Editável"
    const val SIGN_TEMPLATE_DEPOR = "De/Por"
    const val SIGN_TEMPLATE_DEPOR_PARCELADO = "Parcelado"
    const val SIGN_TEMPLATE_LEVE_GANHE_CADA = "leve_ganhe_cada"
    const val SIGN_TEMPLATE_LEVE_GANHE_TOTAL = "leve_ganhe_total"
    const val SIGN_TEMPLATE_NORMAL = "Normal"
    const val SIGN_TYPE_PAPELETA_COMUM_MODELO = "PAPELETA_COMUM_MODELO"

    /**
     * O template leve_ganhe_cada renderiza "Leve X por XX,XX" sem o "R$".
     * O leve_ganhe_total renderiza "Leve X por R$ XX,XX".
     * Normaliza para que todas as etiquetas leve e ganhe mostrem o "R$".
     */
    fun templateNormalizado(template: String?): String? {
        return when (template) {
            SIGN_TEMPLATE_LEVE_GANHE_CADA -> SIGN_TEMPLATE_MODELO
            else -> template
        }
    }

    /**
     * Total do leve e ganhe:
     * - Se promotionPrice existe, ele é o total (ex.: CX BOMBOM 23,98 = 2 x 11,99).
     * - Senão o total é takeAndWinPrice x quantidade (ex.: KIT KAT 10,99 direto).
     */
    fun totalLeveGanhe(promotionPrice: Double?, takeAndWinPrice: Double?, takeAndWinQuantity: Int?): Double {
        if (promotionPrice != null && promotionPrice > 0) return promotionPrice
        val preco = takeAndWinPrice ?: return 0.0
        val qtd = takeAndWinQuantity ?: 1
        return preco * qtd
    }

    /**
     * Total do leve e ganhe conforme o template:
     * - leve_ganhe_total: takeAndWinPrice já é o total (não multiplica).
     * - leve_ganhe_cada (ou outro): promotionPrice quando existe, senão takeAndWinPrice x quantidade.
     */
    fun totalLeveGanhePorTemplate(
        template: String?,
        promotionPrice: Double?,
        takeAndWinPrice: Double?,
        takeAndWinQuantity: Int?
    ): Double {
        if (template == SIGN_TEMPLATE_MODELO) return takeAndWinPrice ?: 0.0
        return totalLeveGanhe(promotionPrice, takeAndWinPrice, takeAndWinQuantity)
    }

    const val MOVEMENT_ALL = ""
    const val MOVEMENT_DEPOR = "De/Por"
    const val MOVEMENT_LEVE = "Leve e Ganhe"
    const val MOVEMENT_COMUM = "Comum"
    const val MOVEMENT_NORMAL = "Normal"
    
    const val SIGN_SIZE_1X1 = "1X1"
    const val SIGN_SIZE_2X1 = "2X1"
    const val SIGN_SIZE_4X1 = "4X1"
    const val SIGN_SIZE_6X1 = "6X1"
    
    const val DATE_FORMAT_API = "2026-06-20"
    
    const val BASE_URL_SL = "https://sl-bff.americanas.io/"
    const val BASE_URL_MINHALOJA = "https://minhaloja-bff.americanas.io/"
}