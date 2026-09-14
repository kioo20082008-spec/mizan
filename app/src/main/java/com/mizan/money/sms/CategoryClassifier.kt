package com.mizan.money.sms

import com.mizan.money.data.CASH_WITHDRAWAL_CATEGORY

object CategoryClassifier {
    val categories = listOf(
        "طعام وشراب","بقالة","مواصلات","وقود","تسوق","فواتير",
        "اتصالات","صحة","ترفيه","اشتراكات","تعليم","تحويلات",
        CASH_WITHDRAWAL_CATEGORY,"أخرى"
    )
    private val rules: List<Pair<String, List<String>>> = listOf(
        "بقالة" to listOf("بنده","كارفور","العثيم","الدانوب","تميمي","لولو","نستو","panda","carrefour","othaim","danube","tamimi","lulu","hyper"),
        "طعام وشراب" to listOf("ستاربكس","كودو","هرفي","ماكدونالدز","الطازج","البيك","كنتاكي","بيتزا","مطعم","كافيه","قهوة","دونات","دانكن","starbucks","mcdonald","kudu","herfy","pizza","kfc","dunkin","cafe","restaurant"),
        "وقود" to listOf("أديل","الدريس","بترومين","نفط","ساسكو","بترو","aldrees","petromin","sasco","adnoc","total","aramco"),
        "مواصلات" to listOf("أوبر","كريم","ليمو","تاكسي","مترو","حافلات","uber","careem","taxi","metro","saptco","bolt"),
        "اتصالات" to listOf("stc","موبايلي","زين","الاتصالات","سلام","mobily","zain","virgin","lebara"),
        "اشتراكات" to listOf("نتفلكس","netflix","شاهد","shahid","spotify","سبوتيفاي","أنغامي","anghami","youtube","icloud","google one","adobe","chatgpt","openai","microsoft","subscription","اشتراك"),
        "صحة" to listOf("صيدلية","النهدي","الدواء","مستشفى","عيادة","مختبر","طبي","nahdi","pharmacy","hospital","clinic","dawaa","seha"),
        "تسوق" to listOf("أمازون","نون","جرير","إكسترا","شي إن","نمشي","amazon","noon","jarir","extra","shein","namshi","ikea","hm","zara","centrepoint","home box","ساكو","saco"),
        "فواتير" to listOf("كهرباء","المياه","الغاز","شركة الكهرباء","sec","marafiq","water","electricity","فاتورة","bill","إيجار","ايجار","rent"),
        "تعليم" to listOf("جامعة","مدرسة","كورس","دورة","udemy","coursera","university","school","tuition"),
        "تحويلات" to listOf("تحويل","حوالة","transfer","remittance","western union","stc pay","urpay","محفظة"),
        // Checked last: only a bare ATM withdrawal with no identifiable merchant
        // should land here, never override a restaurant/grocery/etc. match above.
        CASH_WITHDRAWAL_CATEGORY to listOf("سحب نقدي","صراف آلي","ماكينة صراف","atm","cash withdrawal")
    )
    fun classify(merchant: String?, rawSms: String): String {
        val text = ((merchant ?: "") + " " + rawSms).lowercase()
        for ((cat, keys) in rules) {
            if (keys.any { text.contains(it) }) return cat
        }
        return "أخرى"
    }
}
