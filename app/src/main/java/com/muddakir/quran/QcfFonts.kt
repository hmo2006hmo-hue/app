package com.muddakir.quran

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/* ------------------------------------------------------------------------
 * خطوط QCF4 (مصحف المدينة - عثمان طه - مجمع الملك فهد).
 * كل صفحة من الـ604 لها خط خاص (QCF4_Hafs_01 .. QCF4_Hafs_47)، وخط إضافي
 * لأشرطة أسماء السور (QCF4_QBSML). الأسماء هنا مطابقة تمامًا لحقل "font"
 * الموجود بملفات assets/quran/qcf/page-N.json.
 * ------------------------------------------------------------------------ */
object QcfFonts {

    private val hafsFamilies: Map<String, FontFamily> = mapOf(
        "QCF4_Hafs_01" to FontFamily(Font(R.font.qcf4_hafs_01)),
        "QCF4_Hafs_02" to FontFamily(Font(R.font.qcf4_hafs_02)),
        "QCF4_Hafs_03" to FontFamily(Font(R.font.qcf4_hafs_03)),
        "QCF4_Hafs_04" to FontFamily(Font(R.font.qcf4_hafs_04)),
        "QCF4_Hafs_05" to FontFamily(Font(R.font.qcf4_hafs_05)),
        "QCF4_Hafs_06" to FontFamily(Font(R.font.qcf4_hafs_06)),
        "QCF4_Hafs_07" to FontFamily(Font(R.font.qcf4_hafs_07)),
        "QCF4_Hafs_08" to FontFamily(Font(R.font.qcf4_hafs_08)),
        "QCF4_Hafs_09" to FontFamily(Font(R.font.qcf4_hafs_09)),
        "QCF4_Hafs_10" to FontFamily(Font(R.font.qcf4_hafs_10)),
        "QCF4_Hafs_11" to FontFamily(Font(R.font.qcf4_hafs_11)),
        "QCF4_Hafs_12" to FontFamily(Font(R.font.qcf4_hafs_12)),
        "QCF4_Hafs_13" to FontFamily(Font(R.font.qcf4_hafs_13)),
        "QCF4_Hafs_14" to FontFamily(Font(R.font.qcf4_hafs_14)),
        "QCF4_Hafs_15" to FontFamily(Font(R.font.qcf4_hafs_15)),
        "QCF4_Hafs_16" to FontFamily(Font(R.font.qcf4_hafs_16)),
        "QCF4_Hafs_17" to FontFamily(Font(R.font.qcf4_hafs_17)),
        "QCF4_Hafs_18" to FontFamily(Font(R.font.qcf4_hafs_18)),
        "QCF4_Hafs_19" to FontFamily(Font(R.font.qcf4_hafs_19)),
        "QCF4_Hafs_20" to FontFamily(Font(R.font.qcf4_hafs_20)),
        "QCF4_Hafs_21" to FontFamily(Font(R.font.qcf4_hafs_21)),
        "QCF4_Hafs_22" to FontFamily(Font(R.font.qcf4_hafs_22)),
        "QCF4_Hafs_23" to FontFamily(Font(R.font.qcf4_hafs_23)),
        "QCF4_Hafs_24" to FontFamily(Font(R.font.qcf4_hafs_24)),
        "QCF4_Hafs_25" to FontFamily(Font(R.font.qcf4_hafs_25)),
        "QCF4_Hafs_26" to FontFamily(Font(R.font.qcf4_hafs_26)),
        "QCF4_Hafs_27" to FontFamily(Font(R.font.qcf4_hafs_27)),
        "QCF4_Hafs_28" to FontFamily(Font(R.font.qcf4_hafs_28)),
        "QCF4_Hafs_29" to FontFamily(Font(R.font.qcf4_hafs_29)),
        "QCF4_Hafs_30" to FontFamily(Font(R.font.qcf4_hafs_30)),
        "QCF4_Hafs_31" to FontFamily(Font(R.font.qcf4_hafs_31)),
        "QCF4_Hafs_32" to FontFamily(Font(R.font.qcf4_hafs_32)),
        "QCF4_Hafs_33" to FontFamily(Font(R.font.qcf4_hafs_33)),
        "QCF4_Hafs_34" to FontFamily(Font(R.font.qcf4_hafs_34)),
        "QCF4_Hafs_35" to FontFamily(Font(R.font.qcf4_hafs_35)),
        "QCF4_Hafs_36" to FontFamily(Font(R.font.qcf4_hafs_36)),
        "QCF4_Hafs_37" to FontFamily(Font(R.font.qcf4_hafs_37)),
        "QCF4_Hafs_38" to FontFamily(Font(R.font.qcf4_hafs_38)),
        "QCF4_Hafs_39" to FontFamily(Font(R.font.qcf4_hafs_39)),
        "QCF4_Hafs_40" to FontFamily(Font(R.font.qcf4_hafs_40)),
        "QCF4_Hafs_41" to FontFamily(Font(R.font.qcf4_hafs_41)),
        "QCF4_Hafs_42" to FontFamily(Font(R.font.qcf4_hafs_42)),
        "QCF4_Hafs_43" to FontFamily(Font(R.font.qcf4_hafs_43)),
        "QCF4_Hafs_44" to FontFamily(Font(R.font.qcf4_hafs_44)),
        "QCF4_Hafs_45" to FontFamily(Font(R.font.qcf4_hafs_45)),
        "QCF4_Hafs_46" to FontFamily(Font(R.font.qcf4_hafs_46)),
        "QCF4_Hafs_47" to FontFamily(Font(R.font.qcf4_hafs_47)),
        "QCF4_QBSML" to FontFamily(Font(R.font.qcf4_qbsml))
    )

    /** يرجع FontFamily المطابق لاسم الخط القادم من JSON (مثال: "QCF4_Hafs_07"). */
    fun familyFor(fontName: String): FontFamily =
        hafsFamilies[fontName] ?: FontFamily.Default
}
