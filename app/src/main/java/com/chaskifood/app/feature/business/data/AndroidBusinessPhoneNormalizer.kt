package com.chaskifood.app.feature.business.data

import android.telephony.PhoneNumberUtils
import com.chaskifood.app.feature.business.domain.BusinessPhoneNormalizer
import com.chaskifood.app.feature.business.domain.businessPhoneRegions
import javax.inject.Inject

class AndroidBusinessPhoneNormalizer @Inject constructor() : BusinessPhoneNormalizer {
    override fun toInternational(dialCode: String, nationalNumber: String): String? {
        val region = businessPhoneRegions[dialCode] ?: return null
        val number = nationalNumber.trim()
        if (number.isEmpty() || !number.all { it in '0'..'9' } || dialCode.length - 1 + number.length > 15) return null
        return PhoneNumberUtils.formatNumberToE164(dialCode + number, region)
    }
}
