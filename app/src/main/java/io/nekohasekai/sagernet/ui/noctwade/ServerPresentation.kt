package io.nekohasekai.sagernet.ui.noctwade

import io.nekohasekai.sagernet.database.ProxyEntity
import java.util.Locale
import org.json.JSONObject

internal object ServerPresentation {
    private val codes = Locale.getISOCountries().toSet()
    private val aliases =
        mapOf(
            "russia" to "RU",
            "россия" to "RU",
            "москва" to "RU",
            "germany" to "DE",
            "германия" to "DE",
            "frankfurt" to "DE",
            "netherlands" to "NL",
            "нидерланды" to "NL",
            "amsterdam" to "NL",
            "poland" to "PL",
            "польша" to "PL",
            "warsaw" to "PL",
            "finland" to "FI",
            "финляндия" to "FI",
            "sweden" to "SE",
            "швеция" to "SE",
            "france" to "FR",
            "франция" to "FR",
            "paris" to "FR",
            "turkey" to "TR",
            "турция" to "TR",
            "turkiye" to "TR",
            "usa" to "US",
            "сша" to "US",
            "uk" to "GB",
            "london" to "GB",
            "великобритания" to "GB",
            "japan" to "JP",
            "япония" to "JP",
            "singapore" to "SG",
            "сингапур" to "SG",
            "canada" to "CA",
            "канада" to "CA",
            "china" to "CN",
            "китай" to "CN",
            "hong kong" to "HK",
            "italy" to "IT",
            "италия" to "IT",
            "spain" to "ES",
            "испания" to "ES",
            "switzerland" to "CH",
            "швейцария" to "CH",
            "latvia" to "LV",
            "латвия" to "LV",
            "lithuania" to "LT",
            "литва" to "LT",
            "estonia" to "EE",
            "эстония" to "EE",
            "ukraine" to "UA",
            "украина" to "UA",
            "kazakhstan" to "KZ",
            "казахстан" to "KZ",
        )
    private val aliasPatterns =
        aliases.map { (name, code) ->
            Regex("""(?<![\p{L}\p{N}])${Regex.escape(name)}(?![\p{L}\p{N}])""") to code
        }
    private val isoPattern = Regex("(?<![A-Z0-9])([A-Z]{2})(?![A-Z0-9])")

    fun country(name: String, tested: String? = null, metadata: String? = null): String {
        tested
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it in codes }
            ?.let {
                return it
            }
        metadata
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it in codes }
            ?.let {
                return it
            }
        val points = name.codePoints().toArray()
        for (i in 0 until points.size - 1) if (
            points[i] in 0x1F1E6..0x1F1FF && points[i + 1] in 0x1F1E6..0x1F1FF
        ) {
            val code =
                "${('A'.code+points[i]-0x1F1E6).toChar()}${('A'.code+points[i+1]-0x1F1E6).toChar()}"
            if (code in codes) return code
        }
        val lower = name.lowercase(Locale.ROOT)
        aliasPatterns
            .firstOrNull { it.first.containsMatchIn(lower) }
            ?.let {
                return it.second
            }
        return isoPattern.findAll(name).map { it.groupValues[1] }.firstOrNull { it in codes } ?: ""
    }

    fun country(profile: ProxyEntity): String {
        val metadata =
            runCatching {
                    val j = JSONObject(profile.outboundJson)
                    j.optString("country_code").ifEmpty { j.optString("country") }
                }
                .getOrNull()
        return country(profile.name.orEmpty(), profile.testCountry, metadata)
    }

    /** Flags have their own image; keep the subscription name unchanged in storage. */
    fun title(name: String): String =
        name
            .replace(Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}\\uFE0F?"), "")
            .trim()
            .trimStart('|', '·', '-', '–', '—', ' ')
            .ifBlank { name }

    fun protocol(type: String): String =
        type
            .lowercase(Locale.ROOT)
            .replace(Regex("xray[ _-]*"), "")
            .replace('_', ' ')
            .uppercase(Locale.ROOT)

    fun security(profile: ProxyEntity): String =
        runCatching {
                when (val label = profile.outbound.security().label) {
                    "Raw",
                    "None",
                    "" -> "Без шифрования"
                    "Encrypted" -> "VLESS Encryption"
                    "Reality" -> "REALITY"
                    else -> label
                }
            }
            .getOrDefault("—")
}
