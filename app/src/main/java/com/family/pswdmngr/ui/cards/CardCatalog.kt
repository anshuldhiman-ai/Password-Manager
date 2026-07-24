package com.family.pswdmngr.ui.cards

import androidx.compose.ui.graphics.Color
import com.family.pswdmngr.R
import com.family.pswdmngr.data.CardType

/**
 * Catalog of real debit/credit card products for HDFC, ICICI and SBI.
 * Each product carries the colours of the real plastic so the card face
 * in the vault looks like the card in your wallet.
 *
 * `dark = false` means light plastic (e.g. Millennia white) → dark text.
 */
data class CardProduct(
    val id: String,
    val bank: String,          // key: HDFC / ICICI / SBI
    val type: String,          // CardType.DEBIT or CardType.CREDIT
    val name: String,          // product name printed on the card
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val dark: Boolean = true,  // dark plastic → white text
    val networkHint: String = "AUTO",
    val tagline: String = "",
    /** Official issuer-supplied front artwork bundled for offline use. */
    val artworkRes: Int? = null,
)

object CardCatalog {

    const val BANK_HDFC = "HDFC"
    const val BANK_ICICI = "ICICI"
    const val BANK_SBI = "SBI"

    /** Banks that have a product catalog. */
    val banks = listOf(BANK_HDFC, BANK_ICICI, BANK_SBI)

    fun bankDisplay(key: String) = when (key) {
        BANK_HDFC -> "HDFC Bank"
        BANK_ICICI -> "ICICI Bank"
        BANK_SBI -> "SBI"
        else -> key
    }

    /** Maps free-typed bank names ("hdfc", "State Bank of India") onto a catalog key. */
    fun bankKeyFor(bankName: String): String? {
        val n = bankName.lowercase()
        return when {
            "hdfc" in n -> BANK_HDFC
            "icici" in n -> BANK_ICICI
            "sbi" in n || "state bank" in n -> BANK_SBI
            else -> null
        }
    }

    /* ---------------- HDFC ---------------- */

    private val hdfcDebit = listOf(
        CardProduct("hdfc_d_millennia", BANK_HDFC, CardType.DEBIT, "Millennia",
            Color(0xFFF4F6F8), Color(0xFFBFDCE8), Color(0xFF0A9BB5), dark = false,
            tagline = "5% CashBack", artworkRes = R.drawable.card_hdfc_d_millennia),
        CardProduct("hdfc_d_moneyback", BANK_HDFC, CardType.DEBIT, "MoneyBack",
            Color(0xFF1565C0), Color(0xFF0A2B57), Color(0xFF64B5F6),
            artworkRes = R.drawable.card_hdfc_d_moneyback),
        CardProduct("hdfc_d_platinum", BANK_HDFC, CardType.DEBIT, "EasyShop Platinum",
            Color(0xFF9FA8B5), Color(0xFF4C5560), Color(0xFFE3E8EF),
            artworkRes = R.drawable.card_hdfc_d_platinum),
        CardProduct("hdfc_d_titanium_royale", BANK_HDFC, CardType.DEBIT, "Titanium Royale",
            Color(0xFF6E5F72), Color(0xFF2B2330), Color(0xFFC9A0DC),
            artworkRes = R.drawable.card_hdfc_d_titanium_royale),
        CardProduct("hdfc_d_times", BANK_HDFC, CardType.DEBIT, "Times Points",
            Color(0xFFB71C1C), Color(0xFF3E0808), Color(0xFFFFCDD2),
            artworkRes = R.drawable.card_hdfc_d_times),
        CardProduct("hdfc_d_rewards", BANK_HDFC, CardType.DEBIT, "Rewards",
            Color(0xFF283593), Color(0xFF0D1240), Color(0xFF9FA8DA),
            artworkRes = R.drawable.card_hdfc_d_rewards),
        CardProduct("hdfc_d_rupay_premium", BANK_HDFC, CardType.DEBIT, "EasyShop Platinum RuPay",
            Color(0xFF00695C), Color(0xFF002B25), Color(0xFF80CBC4), networkHint = "RUPAY",
            artworkRes = R.drawable.card_hdfc_d_rupay_premium),
        CardProduct("hdfc_d_easyshop", BANK_HDFC, CardType.DEBIT, "EasyShop Classic",
            Color(0xFF1976D2), Color(0xFF0B3050), Color(0xFF90CAF9),
            artworkRes = R.drawable.card_hdfc_d_easyshop),
        CardProduct("hdfc_d_easyshop_gold", BANK_HDFC, CardType.DEBIT, "EasyShop Gold",
            Color(0xFFC9A227), Color(0xFF5C4409), Color(0xFFFFE082),
            artworkRes = R.drawable.card_hdfc_d_easyshop_gold),
        CardProduct("hdfc_d_business", BANK_HDFC, CardType.DEBIT, "EasyShop Business",
            Color(0xFF37474F), Color(0xFF101B20), Color(0xFF90A4AE),
            artworkRes = R.drawable.card_hdfc_d_business),
        CardProduct("hdfc_d_iocl", BANK_HDFC, CardType.DEBIT, "IOCL",
            Color(0xFFE65100), Color(0xFF4E1B00), Color(0xFFFFB74D),
            artworkRes = R.drawable.card_hdfc_d_iocl),

        /* -- Newly catalogued debit variants (real HDFC EasyShop card faces) -- */
        CardProduct("hdfc_d_platinum_preferred", BANK_HDFC, CardType.DEBIT, "EasyShop Preferred Platinum",
            Color(0xFF1B2A42), Color(0xFF0A121F), Color(0xFFB8C4D6),
            artworkRes = R.drawable.card_hdfc_d_platinum_preferred),
        CardProduct("hdfc_d_platinum_imperia", BANK_HDFC, CardType.DEBIT, "EasyShop Imperia Platinum",
            Color(0xFF6E1F2E), Color(0xFF260A11), Color(0xFFD4AF37),
            tagline = "Priority Banking", artworkRes = R.drawable.card_hdfc_d_platinum_imperia),
        CardProduct("hdfc_d_platinum_classic", BANK_HDFC, CardType.DEBIT, "EasyShop Classic Platinum",
            Color(0xFF17181C), Color(0xFF060607), Color(0xFFB9976B),
            artworkRes = R.drawable.card_hdfc_d_platinum_classic),
        CardProduct("hdfc_d_vishesh", BANK_HDFC, CardType.DEBIT, "Vishesh",
            Color(0xFF7FB4D9), Color(0xFF2E5A78), Color(0xFFC9A15A),
            artworkRes = R.drawable.card_hdfc_d_vishesh),
        CardProduct("hdfc_d_kids_advantage", BANK_HDFC, CardType.DEBIT, "Kid's Advantage",
            Color(0xFF64B5F6), Color(0xFF1E4E7A), Color(0xFFFFC107), networkHint = "RUPAY",
            artworkRes = R.drawable.card_hdfc_d_kids_advantage),
        CardProduct("hdfc_d_womans_advantage", BANK_HDFC, CardType.DEBIT, "Woman's Advantage",
            Color(0xFFAD3E78), Color(0xFF43102C), Color(0xFFFFA6D0),
            artworkRes = R.drawable.card_hdfc_d_womans_advantage),
        CardProduct("hdfc_d_pmjdy", BANK_HDFC, CardType.DEBIT, "RuPay PMJDY",
            Color(0xFFF57C00), Color(0xFF7A3200), Color(0xFFFFE0B2), networkHint = "RUPAY",
            tagline = "Jan Dhan Yojana", artworkRes = R.drawable.card_hdfc_d_pmjdy),
        CardProduct("hdfc_d_rupay_nro", BANK_HDFC, CardType.DEBIT, "EasyShop Platinum RuPay NRO",
            Color(0xFF29ABE2), Color(0xFF0B4A66), Color(0xFF1B3D8F), networkHint = "RUPAY",
            artworkRes = R.drawable.card_hdfc_d_rupay_nro),
        CardProduct("hdfc_d_visa_nro", BANK_HDFC, CardType.DEBIT, "EasyShop Visa Platinum NRO",
            Color(0xFF1B2A42), Color(0xFF0A121F), Color(0xFFB8C4D6),
            artworkRes = R.drawable.card_hdfc_d_visa_nro),
        CardProduct("hdfc_d_giga_business", BANK_HDFC, CardType.DEBIT, "GIGA Business",
            Color(0xFF1A2340), Color(0xFF070B18), Color(0xFF4FC3E0),
            artworkRes = R.drawable.card_hdfc_d_giga_business),
        CardProduct("hdfc_d_infiniti", BANK_HDFC, CardType.DEBIT, "Infiniti",
            Color(0xFF1C1C1E), Color(0xFF000000), Color(0xFFD4AF37),
            tagline = "World Debit Card", artworkRes = R.drawable.card_hdfc_d_infiniti),
        CardProduct("hdfc_d_companion", BANK_HDFC, CardType.DEBIT, "Companion",
            Color(0xFF1A2340), Color(0xFF9FA8B5), Color(0xFFB8C4D6),
            artworkRes = R.drawable.card_hdfc_d_companion),
    )

    private val hdfcCredit = listOf(
        CardProduct("hdfc_c_millennia", BANK_HDFC, CardType.CREDIT, "Millennia",
            Color(0xFFF4F6F8), Color(0xFFB8D8E8), Color(0xFF0A9BB5), dark = false,
            tagline = "5% CashBack", artworkRes = R.drawable.card_hdfc_millennia),
        CardProduct("hdfc_c_moneyback_plus", BANK_HDFC, CardType.CREDIT, "MoneyBack+",
            Color(0xFF1565C0), Color(0xFF082448), Color(0xFF64B5F6),
            artworkRes = R.drawable.card_hdfc_moneyback_plus),
        CardProduct("hdfc_c_freedom", BANK_HDFC, CardType.CREDIT, "Freedom",
            Color(0xFF5C2D91), Color(0xFF200E38), Color(0xFFB39DDB),
            artworkRes = R.drawable.card_hdfc_freedom),
        CardProduct("hdfc_c_regalia_gold", BANK_HDFC, CardType.CREDIT, "Regalia Gold",
            Color(0xFF1A2340), Color(0xFF070B18), Color(0xFFD4AF37),
            artworkRes = R.drawable.card_hdfc_regalia_gold),
        CardProduct("hdfc_c_regalia", BANK_HDFC, CardType.CREDIT, "Regalia",
            Color(0xFF23304F), Color(0xFF0B101F), Color(0xFFC0A860),
            artworkRes = R.drawable.card_hdfc_c_regalia),
        CardProduct("hdfc_c_infinia", BANK_HDFC, CardType.CREDIT, "Infinia Metal",
            Color(0xFF17181C), Color(0xFF060607), Color(0xFFB9976B),
            tagline = "Metal Edition", artworkRes = R.drawable.card_hdfc_infinia),
        CardProduct("hdfc_c_diners_black", BANK_HDFC, CardType.CREDIT, "Diners Club Black",
            Color(0xFF121212), Color(0xFF040404), Color(0xFFCFCFCF), networkHint = "DINERS",
            artworkRes = R.drawable.card_hdfc_diners_black),
        CardProduct("hdfc_c_diners_priv", BANK_HDFC, CardType.CREDIT, "Diners Club Privilege",
            Color(0xFF25303E), Color(0xFF0B1017), Color(0xFF8FB8DE), networkHint = "DINERS",
            artworkRes = R.drawable.card_hdfc_diners_privilege),
        CardProduct("hdfc_c_swiggy", BANK_HDFC, CardType.CREDIT, "Swiggy",
            Color(0xFFFC8019), Color(0xFF7A3200), Color(0xFFFFFFFF),
            tagline = "10% CashBack", artworkRes = R.drawable.card_hdfc_swiggy),
        CardProduct("hdfc_c_tataneu_plus", BANK_HDFC, CardType.CREDIT, "Tata Neu Plus",
            Color(0xFF3D2E7C), Color(0xFF150F30), Color(0xFF9C8CFF), networkHint = "RUPAY",
            artworkRes = R.drawable.card_hdfc_tataneu_plus),
        CardProduct("hdfc_c_tataneu_inf", BANK_HDFC, CardType.CREDIT, "Tata Neu Infinity",
            Color(0xFF241A52), Color(0xFF0B0720), Color(0xFF7C6CE8), networkHint = "RUPAY",
            artworkRes = R.drawable.card_hdfc_tataneu_infinity),
        CardProduct("hdfc_c_indianoil", BANK_HDFC, CardType.CREDIT, "IndianOil",
            Color(0xFFE65100), Color(0xFF441700), Color(0xFFFFCC80),
            artworkRes = R.drawable.card_hdfc_indianoil),
        CardProduct("hdfc_c_irctc", BANK_HDFC, CardType.CREDIT, "IRCTC",
            Color(0xFF0D47A1), Color(0xFF041B40), Color(0xFFFF9800), networkHint = "RUPAY",
            artworkRes = R.drawable.card_hdfc_irctc),
        CardProduct("hdfc_c_pixel", BANK_HDFC, CardType.CREDIT, "PIXEL Play",
            Color(0xFF0F0F1A), Color(0xFF04040A), Color(0xFF00E5FF),
            tagline = "Digital-first", artworkRes = R.drawable.card_hdfc_pixel),
        CardProduct("hdfc_c_marriott", BANK_HDFC, CardType.CREDIT, "Marriott Bonvoy",
            Color(0xFF33234B), Color(0xFF120A20), Color(0xFFC9A05C),
            artworkRes = R.drawable.card_hdfc_marriott),
        CardProduct("hdfc_c_6e", BANK_HDFC, CardType.CREDIT, "6E Rewards IndiGo",
            Color(0xFF001B94), Color(0xFF000A38), Color(0xFF7FA8FF),
            artworkRes = R.drawable.card_hdfc_c_6e),

        /* -- Newly catalogued credit variants (real HDFC card faces) -- */
        CardProduct("hdfc_c_shoppers_stop", BANK_HDFC, CardType.CREDIT, "Shoppers Stop First Citizen",
            Color(0xFF16325C), Color(0xFF071120), Color(0xFFAEB9C9),
            artworkRes = R.drawable.card_hdfc_shoppers_stop),
        CardProduct("hdfc_c_shoppers_stop_black", BANK_HDFC, CardType.CREDIT, "Shoppers Stop First Citizen Black",
            Color(0xFF1A1A1A), Color(0xFF000000), Color(0xFFBEBEBE),
            artworkRes = R.drawable.card_hdfc_shoppers_stop_black),
        CardProduct("hdfc_c_upi_rupay", BANK_HDFC, CardType.CREDIT, "UPI RuPay Credit Card",
            Color(0xFF1B0B33), Color(0xFF3A1450), Color(0xFF00E5FF), networkHint = "RUPAY",
            tagline = "Credit card on UPI", artworkRes = R.drawable.card_hdfc_upi_rupay),
    )

    /* ---------------- ICICI ---------------- */

    private val iciciDebit = listOf(
        CardProduct("icici_d_coral", BANK_ICICI, CardType.DEBIT, "Coral",
            Color(0xFFE85D45), Color(0xFF6E1A0E), Color(0xFFFFB59F),
            tagline = "Gemstone Collection", artworkRes = R.drawable.card_icici_d_coral),
        CardProduct("icici_d_rubyx", BANK_ICICI, CardType.DEBIT, "Rubyx",
            Color(0xFF3E2757), Color(0xFF150A22), Color(0xFFE0525E),
            tagline = "Gemstone Collection", artworkRes = R.drawable.card_icici_d_rubyx),
        CardProduct("icici_d_sapphiro", BANK_ICICI, CardType.DEBIT, "Sapphiro",
            Color(0xFF1F3A6E), Color(0xFF081327), Color(0xFFD4AF37),
            tagline = "Gemstone Collection", artworkRes = R.drawable.card_icici_d_sapphiro),
        CardProduct("icici_d_platinum", BANK_ICICI, CardType.DEBIT, "Platinum",
            Color(0xFF8E9BAA), Color(0xFF3C4653), Color(0xFFE4EBF2),
            artworkRes = R.drawable.card_icici_d_platinum),
        CardProduct("icici_d_expressions", BANK_ICICI, CardType.DEBIT, "Expressions",
            Color(0xFFAD3E78), Color(0xFF43102C), Color(0xFFFFA6D0),
            tagline = "My card, my design", artworkRes = R.drawable.card_icici_d_expressions),
        CardProduct("icici_d_business", BANK_ICICI, CardType.DEBIT, "Business",
            Color(0xFF37474F), Color(0xFF121C21), Color(0xFF90A4AE),
            artworkRes = R.drawable.card_icici_d_business),

        /* -- Newly catalogued debit variants (real ICICI card faces) -- */
        CardProduct("icici_d_coral_plus", BANK_ICICI, CardType.DEBIT, "Coral+",
            Color(0xFFE85D45), Color(0xFF1A0A05), Color(0xFFFFB59F),
            tagline = "Gemstone Collection", artworkRes = R.drawable.card_icici_d_coral_plus),
        CardProduct("icici_d_private_banking", BANK_ICICI, CardType.DEBIT, "Private Banking",
            Color(0xFFD4AF37), Color(0xFF8A6D1E), Color(0xFFFFFFFF), dark = false, networkHint = "VISA",
            tagline = "Visa Infinite", artworkRes = R.drawable.card_icici_d_private_banking),
        CardProduct("icici_d_wealth_world", BANK_ICICI, CardType.DEBIT, "Wealth Management World",
            Color(0xFF3A3F47), Color(0xFF0E1013), Color(0xFFC9CED6),
            artworkRes = R.drawable.card_icici_d_wealth_world),
        CardProduct("icici_d_privilege_rupay", BANK_ICICI, CardType.DEBIT, "Privilege Banking RuPay",
            Color(0xFF6E1F2E), Color(0xFF2A0A11), Color(0xFFE8927C), networkHint = "RUPAY",
            artworkRes = R.drawable.card_icici_d_privilege_rupay),
        CardProduct("icici_d_titanium", BANK_ICICI, CardType.DEBIT, "Titanium",
            Color(0xFFE8720C), Color(0xFF8A2E10), Color(0xFFFFD54F),
            artworkRes = R.drawable.card_icici_d_titanium),
        CardProduct("icici_d_family_banking", BANK_ICICI, CardType.DEBIT, "Family Banking",
            Color(0xFFE8720C), Color(0xFFFFFFFF), Color(0xFF1A1A1A), dark = false,
            tagline = "Family Banking", artworkRes = R.drawable.card_icici_d_family_banking),
    )

    private val iciciCredit = listOf(
        CardProduct("icici_c_coral", BANK_ICICI, CardType.CREDIT, "Coral",
            Color(0xFFE85D45), Color(0xFF611607), Color(0xFFFFB59F),
            tagline = "Gemstone Collection", artworkRes = R.drawable.card_icici_coral),
        CardProduct("icici_c_rubyx", BANK_ICICI, CardType.CREDIT, "Rubyx",
            Color(0xFF3E2757), Color(0xFF120820), Color(0xFFE0525E),
            tagline = "Gemstone Collection", artworkRes = R.drawable.card_icici_rubyx),
        CardProduct("icici_c_sapphiro", BANK_ICICI, CardType.CREDIT, "Sapphiro",
            Color(0xFF1F3A6E), Color(0xFF060F20), Color(0xFFD4AF37),
            tagline = "Gemstone Collection", artworkRes = R.drawable.card_icici_sapphiro),
        CardProduct("icici_c_platinum", BANK_ICICI, CardType.CREDIT, "Platinum Chip",
            Color(0xFF6A7684), Color(0xFF2A323C), Color(0xFFDFE7EF),
            artworkRes = R.drawable.card_icici_platinum),
        CardProduct("icici_c_amazon", BANK_ICICI, CardType.CREDIT, "Amazon Pay",
            Color(0xFF232F3E), Color(0xFF0B0F14), Color(0xFFFF9900),
            tagline = "Everyday rewards", artworkRes = R.drawable.card_icici_c_amazon),
        CardProduct("icici_c_mmt", BANK_ICICI, CardType.CREDIT, "MakeMyTrip",
            Color(0xFF14273E), Color(0xFF060D16), Color(0xFFE73C33),
            artworkRes = R.drawable.card_icici_mmt),
        CardProduct("icici_c_hpcl", BANK_ICICI, CardType.CREDIT, "HPCL Super Saver",
            Color(0xFF00579D), Color(0xFF00213C), Color(0xFFFFD500),
            artworkRes = R.drawable.card_icici_hpcl),
        CardProduct("icici_c_emeralde", BANK_ICICI, CardType.CREDIT, "Emeralde",
            Color(0xFF1B3B33), Color(0xFF081512), Color(0xFF5EC9A7),
            tagline = "Luxury, uncompromised", artworkRes = R.drawable.card_icici_emeralde),
        CardProduct("icici_c_emeralde_metal", BANK_ICICI, CardType.CREDIT, "Emeralde Private Metal",
            Color(0xFF15201D), Color(0xFF050908), Color(0xFFCBB57B),
            tagline = "Metal Edition", artworkRes = R.drawable.card_icici_emeralde_metal),
        CardProduct("icici_c_manu", BANK_ICICI, CardType.CREDIT, "Manchester United",
            Color(0xFFB01513), Color(0xFF3C0403), Color(0xFFFFD54F),
            artworkRes = R.drawable.card_icici_manu),
    )

    /* ---------------- SBI ---------------- */

    private val sbiDebit = listOf(
        CardProduct("sbi_d_classic", BANK_SBI, CardType.DEBIT, "Classic",
            Color(0xFF2E6DA4), Color(0xFF0E2A45), Color(0xFF9FC9EC),
            artworkRes = R.drawable.card_sbi_d_classic),
        CardProduct("sbi_d_silver", BANK_SBI, CardType.DEBIT, "Silver",
            Color(0xFF97A2AD), Color(0xFF454E58), Color(0xFFE6ECF1),
            artworkRes = R.drawable.card_sbi_d_silver),
        CardProduct("sbi_d_global", BANK_SBI, CardType.DEBIT, "Global International",
            Color(0xFF20638C), Color(0xFF082438), Color(0xFF7EC8E3),
            artworkRes = R.drawable.card_sbi_d_global),
        CardProduct("sbi_d_gold", BANK_SBI, CardType.DEBIT, "Gold International",
            Color(0xFFC9A227), Color(0xFF54430C), Color(0xFFFFE082),
            artworkRes = R.drawable.card_sbi_d_gold),
        CardProduct("sbi_d_platinum", BANK_SBI, CardType.DEBIT, "Platinum International",
            Color(0xFF4A5560), Color(0xFF1B2025), Color(0xFFCBD5DE),
            artworkRes = R.drawable.card_sbi_d_platinum),
        CardProduct("sbi_d_intouch", BANK_SBI, CardType.DEBIT, "sbiINTOUCH Tap & Go",
            Color(0xFF00838F), Color(0xFF00323A), Color(0xFF80DEEA),
            artworkRes = R.drawable.card_sbi_d_intouch),
        CardProduct("sbi_d_mycard", BANK_SBI, CardType.DEBIT, "My Card",
            Color(0xFF7E57C2), Color(0xFF2E1D4E), Color(0xFFD1C4E9),
            tagline = "Personalised", artworkRes = R.drawable.card_sbi_d_mycard),
        CardProduct("sbi_d_rupay_premium", BANK_SBI, CardType.DEBIT, "RuPay Platinum",
            Color(0xFF00695C), Color(0xFF00251F), Color(0xFF80CBC4), networkHint = "RUPAY",
            artworkRes = R.drawable.card_sbi_d_rupay_premium),
    )

    private val sbiCredit = listOf(
        CardProduct("sbi_c_simplyclick", BANK_SBI, CardType.CREDIT, "SimplyCLICK",
            Color(0xFF16325C), Color(0xFF071120), Color(0xFFA4D233),
            tagline = "Online rewards", artworkRes = R.drawable.card_sbi_simplyclick),
        CardProduct("sbi_c_simplysave", BANK_SBI, CardType.CREDIT, "SimplySAVE",
            Color(0xFF34495E), Color(0xFF10161C), Color(0xFFF3B431),
            tagline = "Everyday savings", artworkRes = R.drawable.card_sbi_simplysave),
        CardProduct("sbi_c_prime", BANK_SBI, CardType.CREDIT, "SBI Card PRIME",
            Color(0xFF2B2B33), Color(0xFF0C0C10), Color(0xFFD4AF37),
            artworkRes = R.drawable.card_sbi_prime),
        CardProduct("sbi_c_elite", BANK_SBI, CardType.CREDIT, "SBI Card ELITE",
            Color(0xFF3A3F47), Color(0xFF13161A), Color(0xFFC9CED6),
            artworkRes = R.drawable.card_sbi_c_elite),
        CardProduct("sbi_c_cashback", BANK_SBI, CardType.CREDIT, "CASHBACK SBI Card",
            Color(0xFF4527A0), Color(0xFF160C36), Color(0xFF69F0AE),
            tagline = "5% cashback online", artworkRes = R.drawable.card_sbi_cashback),
        CardProduct("sbi_c_bpcl", BANK_SBI, CardType.CREDIT, "BPCL SBI Card",
            Color(0xFF00579D), Color(0xFF001F39), Color(0xFFFFD500),
            artworkRes = R.drawable.card_sbi_bpcl),
        CardProduct("sbi_c_irctc", BANK_SBI, CardType.CREDIT, "IRCTC SBI Card",
            Color(0xFF10375C), Color(0xFF051524), Color(0xFFFF9800), networkHint = "RUPAY",
            artworkRes = R.drawable.card_sbi_irctc),
        CardProduct("sbi_c_pulse", BANK_SBI, CardType.CREDIT, "SBI Card PULSE",
            Color(0xFF880E4F), Color(0xFF33051E), Color(0xFFFF80AB),
            tagline = "Fitness first", artworkRes = R.drawable.card_sbi_pulse),
        CardProduct("sbi_c_air_india", BANK_SBI, CardType.CREDIT, "Air India Signature",
            Color(0xFF8E1B1B), Color(0xFF320808), Color(0xFFFFD54F),
            artworkRes = R.drawable.card_sbi_air_india_signature),
        CardProduct("sbi_c_aurum", BANK_SBI, CardType.CREDIT, "AURUM",
            Color(0xFF1A1A1A), Color(0xFF050505), Color(0xFFC8A951),
            tagline = "Metal Edition", artworkRes = R.drawable.card_sbi_c_aurum),

        /* -- Newly catalogued co-brand / segment cards (real SBI card faces) -- */
        CardProduct("sbi_c_flipkart", BANK_SBI, CardType.CREDIT, "Flipkart SBI Card",
            Color(0xFF0D1B4C), Color(0xFF050B24), Color(0xFFFFC400),
            tagline = "Flipkart & Myntra rewards", artworkRes = R.drawable.card_sbi_flipkart),
        CardProduct("sbi_c_tataneu_plus", BANK_SBI, CardType.CREDIT, "Tata Neu Plus SBI Card",
            Color(0xFF1E6FD9), Color(0xFF4B1F91), Color(0xFF63C7F2),
            artworkRes = R.drawable.card_sbi_tata_neu_plus),
        CardProduct("sbi_c_tataneu_infinity", BANK_SBI, CardType.CREDIT, "Tata Neu Infinity SBI Card",
            Color(0xFF2D1B4E), Color(0xFF120A28), Color(0xFF8A6FD8),
            artworkRes = R.drawable.card_sbi_tata_neu_infinity),
        CardProduct("sbi_c_tata", BANK_SBI, CardType.CREDIT, "Tata SBI Card",
            Color(0xFFC9AF7D), Color(0xFF7A6440), Color(0xFF8B1A1A),
            artworkRes = R.drawable.card_sbi_tata),
        CardProduct("sbi_c_tata_select", BANK_SBI, CardType.CREDIT, "Tata SBI Card SELECT",
            Color(0xFF2B2E33), Color(0xFF0C0D0F), Color(0xFF4FC3E0),
            artworkRes = R.drawable.card_sbi_tata_select),
        CardProduct("sbi_c_phonepe", BANK_SBI, CardType.CREDIT, "PhonePe SBI Card",
            Color(0xFF12172A), Color(0xFF4B1F72), Color(0xFF6A3FD1),
            artworkRes = R.drawable.card_sbi_phonepe),
        CardProduct("sbi_c_phonepe_select", BANK_SBI, CardType.CREDIT, "PhonePe SBI Card SELECT",
            Color(0xFF0B0E14), Color(0xFF1A1030), Color(0xFF6A3FD1),
            artworkRes = R.drawable.card_sbi_phonepe_select),
        CardProduct("sbi_c_paytm", BANK_SBI, CardType.CREDIT, "Paytm SBI Card",
            Color(0xFFDCF1FB), Color(0xFFAEE0F5), Color(0xFF00BCD4), dark = false,
            artworkRes = R.drawable.card_sbi_paytm),
        CardProduct("sbi_c_paytm_select", BANK_SBI, CardType.CREDIT, "Paytm SBI Card SELECT",
            Color(0xFF141414), Color(0xFF000000), Color(0xFFD4AF37),
            artworkRes = R.drawable.card_sbi_paytm_select),
        CardProduct("sbi_c_apollo", BANK_SBI, CardType.CREDIT, "Apollo SBI Card",
            Color(0xFFEFE9E4), Color(0xFFCDE3E3), Color(0xFFE8927C), dark = false,
            artworkRes = R.drawable.card_sbi_apollo),
        CardProduct("sbi_c_apollo_select", BANK_SBI, CardType.CREDIT, "Apollo SBI Card SELECT",
            Color(0xFF1A1A1A), Color(0xFF000000), Color(0xFFFF6A00),
            artworkRes = R.drawable.card_sbi_apollo_select),
        CardProduct("sbi_c_shaurya", BANK_SBI, CardType.CREDIT, "SBI Card SHAURYA",
            Color(0xFF0A2E52), Color(0xFF04121F), Color(0xFF29ABE2),
            tagline = "For the armed & defence forces", artworkRes = R.drawable.card_sbi_shaurya),
        CardProduct("sbi_c_shaurya_select", BANK_SBI, CardType.CREDIT, "SBI Card SHAURYA SELECT",
            Color(0xFF1A1A1A), Color(0xFF000000), Color(0xFFBEBEBE),
            artworkRes = R.drawable.card_sbi_shaurya_select),
        CardProduct("sbi_c_landmark", BANK_SBI, CardType.CREDIT, "Landmark Rewards SBI Card",
            Color(0xFF1B2A42), Color(0xFF0A121F), Color(0xFFB8C4D6),
            artworkRes = R.drawable.card_sbi_landmark),
        CardProduct("sbi_c_landmark_prime", BANK_SBI, CardType.CREDIT, "Landmark Rewards SBI Card PRIME",
            Color(0xFF1C1C1C), Color(0xFF050505), Color(0xFFD0D0D0),
            artworkRes = R.drawable.card_sbi_landmark_prime),
        CardProduct("sbi_c_landmark_select", BANK_SBI, CardType.CREDIT, "Landmark Rewards SBI Card SELECT",
            Color(0xFF6B4F8E), Color(0xFF2E1F45), Color(0xFFE0D6EC),
            artworkRes = R.drawable.card_sbi_landmark_select),
        CardProduct("sbi_c_reliance", BANK_SBI, CardType.CREDIT, "Reliance SBI Card",
            Color(0xFFC81E1E), Color(0xFF6E0B0B), Color(0xFFD4A54A),
            artworkRes = R.drawable.card_sbi_reliance),
        CardProduct("sbi_c_reliance_prime", BANK_SBI, CardType.CREDIT, "Reliance SBI Card PRIME",
            Color(0xFFF2F2F2), Color(0xFFC9C9C9), Color(0xFFD4A54A), dark = false,
            artworkRes = R.drawable.card_sbi_reliance_prime),
        CardProduct("sbi_c_unnati", BANK_SBI, CardType.CREDIT, "SBI Card UNNATI",
            Color(0xFFFF7A1A), Color(0xFF2E7D32), Color(0xFF00B5EF), dark = false,
            tagline = "First credit card, made simple", artworkRes = R.drawable.card_sbi_unnati),
        CardProduct("sbi_c_air_india_platinum", BANK_SBI, CardType.CREDIT, "Air India Platinum",
            Color(0xFF7A1F0E), Color(0xFF2E0A03), Color(0xFFFFA000),
            artworkRes = R.drawable.card_sbi_air_india_platinum),
        CardProduct("sbi_c_bpcl_octane", BANK_SBI, CardType.CREDIT, "BPCL SBI Card OCTANE",
            Color(0xFF1A1408), Color(0xFF000000), Color(0xFFE8A93A),
            tagline = "Premium fuel rewards", artworkRes = R.drawable.card_sbi_bpcl_octane),
        CardProduct("sbi_c_indigo", BANK_SBI, CardType.CREDIT, "IndiGo SBI Card",
            Color(0xFF141A6E), Color(0xFF060A33), Color(0xFFFFFFFF),
            artworkRes = R.drawable.card_sbi_indigo),
        CardProduct("sbi_c_indigo_elite", BANK_SBI, CardType.CREDIT, "IndiGo SBI Card ELITE",
            Color(0xFF2C2470), Color(0xFFB13C93), Color(0xFFFFFFFF),
            artworkRes = R.drawable.card_sbi_indigo_elite),
        CardProduct("sbi_c_irctc_premier", BANK_SBI, CardType.CREDIT, "IRCTC SBI Card Premier",
            Color(0xFF150B33), Color(0xFF03040F), Color(0xFFB84FD1),
            artworkRes = R.drawable.card_sbi_irctc_premier),
        CardProduct("sbi_c_krisflyer_apex", BANK_SBI, CardType.CREDIT, "KrisFlyer SBI Card APEX",
            Color(0xFF1C1C1E), Color(0xFF000000), Color(0xFFD4AF37),
            artworkRes = R.drawable.card_sbi_krisflyer_apex),
        CardProduct("sbi_c_krisflyer", BANK_SBI, CardType.CREDIT, "KrisFlyer SBI Card",
            Color(0xFF1B1345), Color(0xFF06031A), Color(0xFFD4AF37),
            artworkRes = R.drawable.card_sbi_krisflyer),
        CardProduct("sbi_c_miles_elite", BANK_SBI, CardType.CREDIT, "SBI Card MILES ELITE",
            Color(0xFFEBC98C), Color(0xFFC69A4E), Color(0xFF8A5A21), dark = false,
            artworkRes = R.drawable.card_sbi_miles_elite),
        CardProduct("sbi_c_miles", BANK_SBI, CardType.CREDIT, "SBI Card MILES",
            Color(0xFFD9BAC7), Color(0xFFB08897), Color(0xFF7A4B5C), dark = false,
            artworkRes = R.drawable.card_sbi_miles),
        CardProduct("sbi_c_miles_prime", BANK_SBI, CardType.CREDIT, "SBI Card MILES PRIME",
            Color(0xFFBEEAE6), Color(0xFF1C8079), Color(0xFF0D5C56), dark = false,
            artworkRes = R.drawable.card_sbi_miles_prime),
        CardProduct("sbi_c_vistara_prime", BANK_SBI, CardType.CREDIT, "Club Vistara SBI Card PRIME",
            Color(0xFF122A54), Color(0xFF060E1E), Color(0xFFE08A3C),
            artworkRes = R.drawable.card_sbi_vistara_prime),
        CardProduct("sbi_c_vistara", BANK_SBI, CardType.CREDIT, "Club Vistara SBI Card",
            Color(0xFF4A1F4A), Color(0xFF1A0A1F), Color(0xFFE08A3C),
            artworkRes = R.drawable.card_sbi_vistara),
    )

    private val all: List<CardProduct> =
        hdfcDebit + hdfcCredit + iciciDebit + iciciCredit + sbiDebit + sbiCredit

    fun byId(id: String): CardProduct? = if (id.isBlank()) null else all.firstOrNull { it.id == id }

    /** All products of a bank for a given card type. */
    fun productsFor(bankKey: String, type: String): List<CardProduct> =
        all.filter { it.bank == bankKey && it.type == type }
}
