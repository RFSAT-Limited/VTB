package com.rfsat.vtb.profiles

enum class ClickUnit { MOA_QUARTER, MOA_EIGHTH, MRAD_TENTH }

/**
 * Default is the Vector Optics Continental 5-30x56 (VCT-34FFP Tactical
 * MIL / VEC-MBR): 0.1 MRAD/click, 26 MRAD elevation travel, 16 MRAD
 * windage travel (manufacturer spec). Editable in-app if you swap scopes.
 */
data class ScopeProfile(
    val name: String = "Vector Optics Continental 5-30x56 (VCT-34FFP Tactical MIL)",
    val clickUnit: ClickUnit = ClickUnit.MRAD_TENTH,
    val maxElevationTravelMoa: Double = 26.0 * 3.43775, // 26 MRAD elevation travel
    val maxWindageTravelMoa: Double = 16.0 * 3.43775    // 16 MRAD windage travel
) {
    /** Angular value of one click, in the scope's own units. */
    val clickValue: Double get() = when (clickUnit) {
        ClickUnit.MOA_QUARTER -> 0.25
        ClickUnit.MOA_EIGHTH -> 0.125
        ClickUnit.MRAD_TENTH -> 0.1
    }
    val clickUnitIsMoa: Boolean get() = clickUnit != ClickUnit.MRAD_TENTH

    companion object {
        val DEFAULT = ScopeProfile()
    }
}
