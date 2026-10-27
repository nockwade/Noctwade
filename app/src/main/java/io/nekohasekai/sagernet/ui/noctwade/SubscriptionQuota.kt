package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyGroup
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date

/**
 * Provider-reported account usage. Local session counters must not be added to it (would
 * double-count).
 */
internal data class SubscriptionQuota(
    val groupId: Long,
    val name: String,
    val amount: String,
    val allowance: String,
    val detail: String,
    val percent: Int?,
    val exhausted: Boolean,
    /** True for subscription groups: the card offers a refresh button. */
    val subscription: Boolean = false,
) {
    companion object {
        private const val KIB = 1024.0

        /**
         * Binary units, like v2rayN, Happ, Hiddify and Clash: 1 GB = 1024³ bytes. The previous
         * decimal division showed about 7% more than those clients for the same subscription.
         */
        fun formatBytes(context: Context, bytes: Long): String {
            val value = bytes.coerceAtLeast(0L).toDouble()
            val gb = value / (KIB * KIB * KIB)
            return when {
                gb >= KIB ->
                    context.getString(R.string.nw_tb, DecimalFormat("0.##").format(gb / KIB))
                gb >= 100 -> context.getString(R.string.nw_gb, DecimalFormat("0").format(gb))
                gb >= 1 -> context.getString(R.string.nw_gb, DecimalFormat("0.#").format(gb))
                else ->
                    context.getString(
                        R.string.nw_mb,
                        DecimalFormat("0").format(value / (KIB * KIB)),
                    )
            }
        }

        fun from(context: Context, group: ProxyGroup?): SubscriptionQuota {
            val name = group?.displayName() ?: context.getString(R.string.nw_no_subscription)
            val info = group?.subInfo
            if (group == null || !group.isSubscription)
                return SubscriptionQuota(
                    group?.id ?: 0,
                    name,
                    "—",
                    context.getString(R.string.nw_quota_manual),
                    context.getString(R.string.nw_quota_manual_hint),
                    null,
                    false,
                )
            if (info == null || !info.valid || !info.hasQuota)
                return SubscriptionQuota(
                    group.id,
                    name,
                    "—",
                    context.getString(R.string.nw_quota_unknown),
                    context.getString(R.string.nw_quota_unknown_hint),
                    null,
                    false,
                    subscription = true,
                )
            val upload = info.upload.coerceAtLeast(0L)
            val download = info.download.coerceAtLeast(0L)
            val used = if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
            val total = info.total.coerceAtLeast(0L)
            // "38.4 GB / 100 GB", or "440 GB / ∞" when the provider sets no limit.
            val allowance =
                if (total > 0) context.getString(R.string.nw_quota_of, formatBytes(context, total))
                else context.getString(R.string.nw_quota_unlimited)
            val stamp =
                if (group.subLastUpdate > 0)
                    context.getString(
                        R.string.nw_quota_updated,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(group.subLastUpdate * 1000)),
                    )
                else context.getString(R.string.nw_quota_provider)
            return SubscriptionQuota(
                group.id,
                name,
                formatBytes(context, used),
                allowance,
                stamp,
                if (total > 0) (used.toDouble() / total * 100).coerceIn(0.0, 100.0).toInt()
                else null,
                info.isExpired() || total > 0 && used >= total,
                subscription = true,
            )
        }
    }
}
