package com.app.photocloud.data.sync

import java.security.MessageDigest

class RobokassaService {
    companion object {
        private const val MERCHANT_LOGIN = "e1cloud.ru" // TODO: Replace with real one
        private const val PASSWORD_1 = "FaIWZL95RuPl398tdTXp" // TODO: Replace with real one
        private const val BASE_URL = "https://auth.robokassa.ru/Merchant/Index.aspx"
        
        const val SUCCESS_URL = "https://photocloud.app/success"
        const val FAIL_URL = "https://photocloud.app/fail"

        fun generatePaymentUrl(invId: Int, outSum: String, description: String): String {
            val signatureValue = generateSignature(outSum, invId)
            
            return "$BASE_URL?" +
                    "MerchantLogin=$MERCHANT_LOGIN&" +
                    "OutSum=$outSum&" +
                    "InvId=$invId&" +
                    "Description=$description&" +
                    "SignatureValue=$signatureValue"
        }

        private fun generateSignature(outSum: String, invId: Int): String {
            val stringToHash = "$MERCHANT_LOGIN:$outSum:$invId:$PASSWORD_1"
            return md5(stringToHash).lowercase()
        }

        private fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            return digest.joinToString("") {
                "%02x".format(it)
            }
        }
    }
}
