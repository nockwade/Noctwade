package io.nekohasekai.sagernet.route

import java.util.zip.CRC32

object RuleSets {
    /**
     * Versioned snapshots for the user's OutIP preset, named exactly like the original
     * geosite/geoip lists (MetaCubeX / RoscomVPN). Where the rule-set catalog knows the name, the
     * generated config uses the live list and these files only seed it offline (initial_path);
     * names the catalog does not have stay local. Copied to the core directory.
     */
    val bundledFiles: Map<String, String> =
        mapOf(
            "geoip-private" to "meta-private-ips.srs",
            "geosite-private" to "meta-private.srs",
            "geosite-oisd-big" to "oisd-big.srs",
            "geosite-category-ads-all" to "meta-category-ads-all.srs",
            "geosite-win-spy" to "meta-win-spy.srs",
            "geosite-torrent" to "roscom-torrent.srs",
            "geosite-category-ru" to "meta-category-ru.srs",
            "geosite-microsoft" to "meta-microsoft.srs",
            "geosite-steam" to "meta-steam.srs",
            "geosite-whitelist" to "roscom-whitelist.srs",
        )

    /**
     * Names used by profiles saved by earlier builds (client-prefixed); they keep resolving to the same
     * files.
     */
    private val legacyNames: Map<String, String> =
        mapOf(
            "geoip-noctwade-private" to "geoip-private",
            "geosite-noctwade-private" to "geosite-private",
            "geosite-noctwade-oisd-big" to "geosite-oisd-big",
            "geosite-noctwade-category-ads-all" to "geosite-category-ads-all",
            "geosite-noctwade-win-spy" to "geosite-win-spy",
            "geosite-noctwade-torrent" to "geosite-torrent",
            "geosite-noctwade-category-ru" to "geosite-category-ru",
            "geosite-noctwade-microsoft" to "geosite-microsoft",
            "geosite-noctwade-steam" to "geosite-steam",
            "geosite-noctwade-whitelist" to "geosite-whitelist",
        )

    fun canonicalName(entry: String): String = legacyNames[entry] ?: entry

    fun bundledFile(tag: String): String? =
        bundledFiles[canonicalName(tag)]?.let { "noctwade-rule-sets/$it" }

    /**
     * RoscomVPN publishes domain/IP lists separately. DNS selectors must use the same hashed tag as
     * routing.
     */
    fun isDomainList(entry: String): Boolean =
        entry.startsWith("geosite-") ||
            (isUrl(entry) && UrlText.parse(entry)?.path?.contains("/roscomvpn-geosite/") == true)

    fun isIpList(entry: String): Boolean =
        entry.startsWith("geoip-") ||
            (isUrl(entry) && UrlText.parse(entry)?.path?.contains("/roscomvpn-geoip/") == true)

    const val ADBLOCK_TAG = "throne-adblocksingbox"
    const val ADBLOCK_URL =
        "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblocksingbox.srs"

    /** ruleset_mirror values (Const.hpp:51-61). */
    const val MIRROR_GITHUB = 0
    const val MIRROR_CLOUDFLARE = 1
    const val MIRROR_GCORE = 2
    const val MIRROR_QUANTIL = 3
    const val MIRROR_FASTLY = 4
    const val MIRROR_CDN = 5

    /** An http(s) URL whose file name contains ".srs" (get_rule_set_name, RouteRule.h:90-97). */
    fun isUrl(entry: String): Boolean {
        val parts = UrlText.parse(entry.trim()) ?: return false
        if (parts.scheme != "http" && parts.scheme != "https") return false
        return UrlText.fileName(entry.trim()).contains(".srs")
    }

    /**
     * The rule-set tag of a rule_set entry: names unchanged; URLs become `<file with
     * .srs→-srs>-<crc32 of the URL>`. The desktop hashes with qHash, but the tag only has to be
     * stable and shared by the rule and its definition.
     */
    fun tagFor(entry: String): String {
        val e = entry.trim()
        if (!isUrl(e)) return e
        val crc = CRC32().apply { update(e.toByteArray(Charsets.UTF_8)) }.value
        return UrlText.fileName(e).replace(".srs", "-srs") +
            "-" +
            java.lang.Long.toHexString(crc).padStart(8, '0')
    }

    /**
     * get_jsdelivr_link (generate.h:65-96): raw.githubusercontent.com URLs through the chosen
     * jsDelivr mirror.
     */
    /**
     * RoscomVPN renamed the default branch of its list repositories from main to master; links
     * saved by older templates now return 404. Only the download address changes, so rule tags
     * (hashed from the stored entry) stay identical.
     */
    private val roscomMain =
        Regex(
            "^(https://raw\\.githubusercontent\\.com/hydraponique/roscomvpn-(?:geosite|geoip))/main/"
        )

    fun currentUrl(url: String): String = roscomMain.replace(url.trim(), "$1/master/")

    fun mirrorLink(original: String, mirror: Int): String {
        val url = currentUrl(original)
        if (mirror == MIRROR_GITHUB) return url
        val parts = UrlText.parse(url) ?: return url
        if (parts.host != "raw.githubusercontent.com") return url
        val sb =
            StringBuilder(
                when (mirror) {
                    MIRROR_GCORE -> "https://gcore.jsdelivr.net/gh"
                    MIRROR_QUANTIL -> "https://quantil.jsdelivr.net/gh"
                    MIRROR_FASTLY -> "https://fastly.jsdelivr.net/gh"
                    MIRROR_CDN -> "https://cdn.jsdelivr.net/gh"
                    else -> "https://testingcf.jsdelivr.net/gh"
                }
            )
        var index = 0
        for (segment in parts.path.split('/')) {
            if (segment.isEmpty()) continue
            sb.append(if (index == 2) '@' else '/').append(segment)
            index++
        }
        return sb.toString()
    }
}
