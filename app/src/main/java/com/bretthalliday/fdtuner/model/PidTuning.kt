package com.bretthalliday.fdtuner.model

/**
 * PID tuning domain constants and the factory reference ladder.
 *
 * KI/KP relationship: only the three KI values are user-editable; each KP is DERIVED as
 * KP = KI × [KP_KI_RATIO]. The ratio is a single constant so it can be changed in one place if
 * the relationship turns out to differ for a given controller/motor.
 *
 * The reference ladder is DISPLAY-ONLY — there is no apply/stage/write path for it. Only sets
 * 4, 6 and 9 have official FarDriver labels; the others are intentionally unlabeled ("—").
 */
object PidTuning {

    /** KP = KI × this. Single source of truth — adjust here if the relationship differs. */
    const val KP_KI_RATIO = 10

    val KI_NAMES = listOf("StartKI", "MidKI", "MaxKI")
    val KP_NAMES = listOf("StartKP", "MidKP", "MaxKP")

    /** KI name -> its paired KP name (and the reverse). */
    val KP_FOR_KI = mapOf("StartKI" to "StartKP", "MidKI" to "MidKP", "MaxKI" to "MaxKP")
    val KI_FOR_KP = mapOf("StartKP" to "StartKI", "MidKP" to "MidKI", "MaxKP" to "MaxKI")

    /** Paraphrased from FarDriver's manual — shown near the table and in the PID edit dialog. */
    const val WARNING =
        "PID values must match the motor and battery. Higher power and voltage generally need " +
        "smaller PID. Setting them incorrectly can cause abnormal operation, MOE/OVER/PHASE " +
        "faults, or burn the controller. Don't guess — start from a documented set close to " +
        "your setup and adjust only with experienced guidance."

    /** One row of the factory PID reference ladder. */
    data class RefRow(
        val set: Int,
        val startKI: Int, val midKI: Int, val maxKI: Int,
        val startKP: Int, val midKP: Int, val maxKP: Int,
        val label: String,
    )

    /** Factory PID tune ladder. Labels only for the officially-named sets (4, 6, 9). */
    val REFERENCE: List<RefRow> = listOf(
        RefRow(1, 16, 16, 24, 160, 160, 240, "—"),
        RefRow(2, 8, 16, 24, 80, 160, 240, "—"),
        RefRow(3, 8, 8, 12, 80, 80, 120, "—"),
        RefRow(4, 4, 8, 12, 40, 80, 120, "Medium power default"),
        RefRow(5, 4, 4, 6, 40, 40, 60, "—"),
        RefRow(6, 2, 4, 6, 20, 40, 60, "High power default"),
        RefRow(7, 2, 2, 3, 20, 20, 30, "—"),
        RefRow(8, 1, 2, 3, 10, 20, 30, "—"),
        RefRow(9, 1, 1, 1, 10, 10, 10, "Surfboard default"),
    )
}
