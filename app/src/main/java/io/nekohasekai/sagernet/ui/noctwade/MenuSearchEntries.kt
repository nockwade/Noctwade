package io.nekohasekai.sagernet.ui.noctwade

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.noctwade.MenuSearchIndex.Entry

/**
 * Everything the menu search can find. One line per setting: key, title, summary (0 = none). Keys
 * must match the preference keys so the result can scroll to them.
 */
internal object MenuSearchEntries {
    private const val NOCTWADE_PERSONALIZATION =
        "io.nekohasekai.sagernet.ui.noctwade.NoctwadePersonalizationFragment"
    private const val NOCTWADE_ROUTING =
        "io.nekohasekai.sagernet.ui.noctwade.NoctwadeRoutingFragment"
    private const val PRESET_SETTINGS = "io.nekohasekai.sagernet.ui.settings.PresetSettingsFragment"
    private const val TESTING_SETTINGS =
        "io.nekohasekai.sagernet.ui.settings.TestingSettingsFragment"
    private const val CORE_SETTINGS = "io.nekohasekai.sagernet.ui.settings.CoreSettingsFragment"
    private const val DNS_SETTINGS = "io.nekohasekai.sagernet.ui.settings.DnsSettingsFragment"
    private const val DNS_ADVANCED_SETTINGS =
        "io.nekohasekai.sagernet.ui.settings.DnsAdvancedSettingsFragment"
    private const val GENERAL_SETTINGS =
        "io.nekohasekai.sagernet.ui.settings.GeneralSettingsFragment"
    private const val NOCTWADE_INTERFACE_SETTINGS =
        "io.nekohasekai.sagernet.ui.noctwade.NoctwadeInterfaceSettingsFragment"
    private const val INBOUND_SETTINGS =
        "io.nekohasekai.sagernet.ui.settings.InboundSettingsFragment"
    private const val TUN_SETTINGS = "io.nekohasekai.sagernet.ui.settings.TunSettingsFragment"
    private const val SUBSCRIPTION_SETTINGS =
        "io.nekohasekai.sagernet.ui.settings.SubscriptionSettingsFragment"
    private const val WARP_SETTINGS = "io.nekohasekai.sagernet.ui.settings.WarpSettingsFragment"
    private const val XRAY_GEO_SETTINGS =
        "io.nekohasekai.sagernet.ui.settings.XrayGeoSettingsFragment"

    val all: List<Entry> by lazy {
        buildList {
            screen(NOCTWADE_PERSONALIZATION, R.string.nw_personalization) {
                add("nw_theme_cyber", R.string.nw_theme_cyber, 0)
                add("nw_theme_panther", R.string.nw_theme_panther, 0)
                add("nw_theme_dubai", R.string.nw_theme_dubai, 0)
                add("nw_theme_orbit", R.string.nw_theme_orbit, 0)
                add("nw_theme_planet", R.string.nw_theme_planet, 0)
                add("nw_theme_alpine", R.string.nw_theme_alpine, 0)
                add("nw_theme_choose", R.string.nw_theme_choose, 0)
                add("nw_theme_sakura", R.string.nw_theme_sakura, 0)
                add("nw_theme_china", R.string.nw_theme_china, 0)
                add("nw_live_background", R.string.nw_live_background, 0)
                add("nw_eco_mode", R.string.nw_eco_mode, R.string.nw_eco_hint)
                add("nw_theme_aurora", R.string.nw_theme_aurora, 0)
                add("nw_theme_ocean", R.string.nw_theme_ocean, 0)
                add("nw_theme_sunset", R.string.nw_theme_sunset, 0)
                add("nw_theme_prism", R.string.nw_theme_prism, 0)
                add("nw_theme_max", R.string.nw_theme_max, 0)
                add("personalization", R.string.nw_personalization, 0)
            }
            screen(NOCTWADE_ROUTING, R.string.menu_route) { add("routing", R.string.menu_route, 0) }
            screen(NOCTWADE_PERSONALIZATION, R.string.nw_personalization) {
                add("themes", R.string.nw_theme_max, 0)
                add("animation", R.string.nw_live_background, 0)
            }
            screen(PRESET_SETTINGS, R.string.settings_presets) {
                add("mux_protocol", R.string.mux_type, 0)
                add("mux_concurrency", R.string.preset_concurrency, 0)
                add("mux_padding", R.string.padding, R.string.preset_mux_padding_sum)
                add(
                    "mux_default_on",
                    R.string.preset_default_on,
                    R.string.preset_mux_default_on_sum,
                )
                add("xray_mux_concurrency", R.string.preset_concurrency, 0)
                add(
                    "xray_mux_default_on",
                    R.string.preset_default_on,
                    R.string.preset_xray_mux_default_on_sum,
                )
                add("fragment_implementation", R.string.fragment_implementation, 0)
                add("fragment_size", R.string.fragment_size, 0)
                add("fragment_sleep", R.string.fragment_sleep, 0)
                add(
                    "fragment_default_on",
                    R.string.preset_default_on,
                    R.string.preset_fragment_default_on_sum,
                )
                add(
                    "tls_tricks_default_on",
                    R.string.preset_default_on,
                    R.string.preset_tls_tricks_default_on_sum,
                )
                add("utlsFingerprint", R.string.utls_fingerprint, 0)
                add("h2_idle_timeout", R.string.quic_idle_timeout, 0)
                add("h2_keep_alive_period", R.string.quic_keep_alive_period, 0)
                add("h2_stream_receive_window", R.string.hysteria_stream_receive_window, 0)
                add("h2_connection_receive_window", R.string.hysteria_connection_receive_window, 0)
                add("h2_max_concurrent_streams", R.string.quic_max_concurrent_streams, 0)
                add("quic_initial_packet_size", R.string.quic_initial_packet_size, 0)
                add(
                    "quic_disable_path_mtu_discovery",
                    R.string.hysteria_disable_mtu_discovery,
                    R.string.preset_pmtud_default_on_sum,
                )
            }
            screen(TESTING_SETTINGS, R.string.settings_testing) {
                add("test_url", R.string.connection_test_url, 0)
                add("direct_test_url", R.string.direct_test_url, 0)
                add(
                    "exit_country_lookup",
                    R.string.exit_country_lookup,
                    R.string.exit_country_lookup_sum,
                )
                add("exit_lookup_provider", R.string.exit_lookup_provider, 0)
                add("speed_test_mode", R.string.speed_test_mode, 0)
                add("speed_test_timeout_ms", R.string.speed_test_timeout_ms, 0)
                add("simple_dl_url", R.string.simple_download_url, 0)
            }
            screen(CORE_SETTINGS, R.string.settings_core) {
                add("log_level", R.string.log_level, 0)
                add("xray_log_level", R.string.xray_log_level, 0)
                add("enable_stats", R.string.enable_stats, R.string.enable_stats_sum)
                add(
                    "disable_traffic_stats",
                    R.string.disable_traffic_stats,
                    R.string.disable_traffic_stats_sum,
                )
                add(
                    "coreClashApiEnabled",
                    R.string.enable_clash_api,
                    R.string.enable_clash_api_summary,
                )
                add("coreClashApiPort", R.string.listen_port, 0)
                add("core_box_clash_listen_addr", R.string.listen_address, 0)
                add("core_box_clash_api_secret", R.string.clash_api_secret, 0)
                add(
                    "coreApiDashboardEnabled",
                    R.string.api_dashboard_enable,
                    R.string.api_dashboard_enable_sum,
                )
                add("coreApiDashboardPort", R.string.listen_port, 0)
                add("core_box_api_secret", R.string.clash_api_secret, 0)
                add("xray_vless_preference", R.string.xray_vless_preference, 0)
                add("xrayGeoAssets", R.string.xray_geo_assets, R.string.xray_geo_assets_sum)
                add("skip_cert", R.string.global_allow_insecure, R.string.skip_cert_sum)
                add("use_mozilla_certs", R.string.use_mozilla_certs, 0)
                add("enable_ntp", R.string.enable_ntp, R.string.enable_ntp_sum)
                add("ntp_server_address", R.string.ntp_server_address, 0)
                add("ntp_server_port", R.string.ntp_server_port, 0)
                add("ntp_interval", R.string.ntp_interval, 0)
                add("ntp_outbound", R.string.ntp_outbound, 0)
            }
            screen(DNS_SETTINGS, R.string.settings_dns) {
                add("remote_dns", R.string.remote_dns, 0)
                add(
                    "remote_dns_disable_ipv6",
                    R.string.remote_dns_disable_ipv6,
                    R.string.dns_disable_ipv6_sum,
                )
                add("direct_dns", R.string.direct_dns, 0)
                add(
                    "direct_dns_disable_ipv6",
                    R.string.direct_dns_disable_ipv6,
                    R.string.dns_disable_ipv6_sum,
                )
                add("core_box_underlying_dns", R.string.underlying_dns, 0)
                add("dns_final_out", R.string.dns_final_out, 0)
                add("enable_dns_routing", R.string.enable_dns_routing, R.string.dns_routing_message)
                add("fakedns", R.string.enable_fakedns, R.string.fakedns_message)
                add(
                    "fakeip_disable_ipv6",
                    R.string.fakeip_disable_ipv6,
                    R.string.fakeip_disable_ipv6_sum,
                )
                add("dns_use_hosts", R.string.dns_use_hosts, R.string.dns_use_hosts_sum)
                add("dns_predefined_enable", R.string.dns_predefined_enable, 0)
                add("dns_predefined_rules", R.string.dns_predefined_rules, 0)
                add("dnsAdvanced", R.string.dns_advanced, R.string.dns_advanced_sum)
                add("use_dns_object", R.string.use_dns_object, R.string.use_dns_object_sum)
                add("dns_object", R.string.dns_object, 0)
            }
            screen(DNS_ADVANCED_SETTINGS, R.string.settings_general) {
                add("dns_cache_capacity", R.string.dns_cache_capacity, 0)
                add("dns_disable_cache", R.string.dns_disable_cache, 0)
                add("dns_disable_expire", R.string.dns_disable_expire, 0)
                add("dns_optimistic", R.string.dns_optimistic, R.string.dns_optimistic_sum)
                add("dns_optimistic_timeout", R.string.dns_optimistic_timeout, 0)
                add("dns_persist_cache", R.string.dns_persist_cache, R.string.dns_persist_cache_sum)
                add("dns_query_timeout", R.string.dns_query_timeout, 0)
                add("dns_reverse_mapping", R.string.dns_reverse_mapping, 0)
            }
            screen(GENERAL_SETTINGS, R.string.settings_general) {
                add(ServerListOrder.KEY, R.string.nw_server_order, 0)
                add("serviceMode", R.string.service_mode, 0)
                add("remember_enable", R.string.auto_connect, R.string.auto_connect_summary)
                add("alwaysOnVpn", R.string.always_on_vpn, 0)
                add("networkChangeResetConnections", R.string.network_change_reset_connections, 0)
                add("wakeResetConnections", R.string.wake_reset_connections, 0)
                add("batteryOptimization", R.string.battery_optimization, 0)
                add(
                    "backgroundRestrictions",
                    R.string.oem_background,
                    R.string.oem_background_summary,
                )
                add("meteredNetwork", R.string.metered, R.string.metered_summary)
                add(
                    "hideFromRecentApps",
                    R.string.hide_from_recent_apps,
                    R.string.hide_from_recent_apps_summary,
                )
                add(
                    "skip_delete_confirmation",
                    R.string.skip_delete_confirmation,
                    R.string.skip_delete_confirmation_sum,
                )
                add("allow_beta_update", R.string.allow_beta_update, R.string.allow_beta_update_sum)
                add("updateCheckAuto", R.string.update_check_auto, R.string.update_check_auto_sum)
            }
            screen(NOCTWADE_INTERFACE_SETTINGS, R.string.settings_appearance) {
                add("appLanguage", R.string.language, 0)
                add(
                    "alwaysShowAddress",
                    R.string.always_show_address,
                    R.string.always_show_address_sum,
                )
                add(
                    "show_config_security",
                    R.string.show_config_security,
                    R.string.show_config_security_sum,
                )
                add("showDirectSpeed", R.string.show_direct_speed, R.string.show_direct_speed_sum)
                add("showGroupInNotification", R.string.show_group_in_notification, 0)
                add("notificationActions", R.string.notification_actions, 0)
                add("notificationPermission", R.string.notification_permission, 0)
            }
            screen(INBOUND_SETTINGS, R.string.settings_inbound) {
                add(
                    "disable_mixed_inbound",
                    R.string.disable_mixed_inbound,
                    R.string.disable_mixed_inbound_sum,
                )
                add("inbound_socks_port", R.string.port_proxy, 0)
                add(
                    "random_inbound_port",
                    R.string.random_inbound_port,
                    R.string.random_inbound_port_sum,
                )
                add("inboundAllowLan", R.string.allow_access, R.string.allow_access_sum)
                add("appendHttpProxy", R.string.append_http_proxy, R.string.append_http_proxy_sum)
                add("httpProxyBypass", R.string.http_proxy_bypass, 0)
                add("inbound_auth", R.string.inbound_auth, R.string.inbound_auth_sum)
                add("inbound_user", R.string.username, 0)
                add("inbound_pass", R.string.password, 0)
                add("core_dns_in_port", R.string.dns_in_port, 0)
                add("custom_inbound", R.string.custom_inbound, 0)
            }
            screen(TUN_SETTINGS, R.string.settings_tun) {
                add("vpn_mtu", R.string.mtu, 0)
                add("vpn_ipv6", R.string.vpn_ipv6, R.string.vpn_ipv6_sum)
                add(
                    "enable_tun_routing",
                    R.string.enable_tun_routing,
                    R.string.enable_tun_routing_sum,
                )
                add(
                    "tunBypassPrivateRanges",
                    R.string.bypass_private_ranges,
                    R.string.bypass_private_ranges_sum,
                )
                add("vpn_private_ranges", R.string.private_ranges, 0)
                add("tunRestoreRanges", R.string.restore_default_ranges, 0)
                add("proxyApps", R.string.proxied_apps, R.string.proxied_apps_summary)
                add("vpn_tun_ipv4_cidr", R.string.tun_ipv4_cidr, 0)
                add("vpn_tun_ipv6_cidr", R.string.tun_ipv6_cidr, 0)
                add("tunRestoreAddresses", R.string.restore_default_addresses, 0)
            }
            screen(SUBSCRIPTION_SETTINGS, R.string.subscriptions) {
                add("user_agent2", R.string.grp_settings_user_agent, 0)
                add("subAutoUpdateEnabled", R.string.grp_sub_auto_update, 0)
                add("subAutoUpdateInterval", R.string.grp_sub_auto_update_interval, 0)
                add(
                    "sub_respect_server_interval",
                    R.string.sub_respect_server_interval_title,
                    R.string.sub_respect_server_interval_tip,
                )
                add("sub_tls_version", R.string.sub_tls_version_title, 0)
                add("sub_http_version", R.string.sub_http_version_title, 0)
                add("sub_clear", R.string.grp_sub_clear, 0)
                add(
                    "sub_show_change_popup",
                    R.string.grp_sub_show_change_popup,
                    R.string.grp_sub_show_change_popup_tip,
                )
                add("sub_send_hwid", R.string.grp_sub_send_hwid, 0)
                add("sub_custom_hwid_params", R.string.grp_sub_custom_hwid_params, 0)
                add(
                    "allow_stopping_active_profile",
                    R.string.grp_allow_stopping_active_profile,
                    R.string.grp_allow_stopping_active_profile_tip,
                )
                add("net_use_proxy", R.string.net_use_proxy, R.string.net_use_proxy_sum)
                add("net_insecure", R.string.net_insecure, R.string.net_insecure_sum)
                add("appTLSVersion", R.string.app_tls_version, 0)
            }
            screen(WARP_SETTINGS, R.string.settings_general) {
                add("enable_warp", R.string.warp_enable, R.string.warp_enable_sum)
                add("warp_mode", R.string.warp_mode, 0)
                add("warp_ep", R.string.warp_endpoint, 0)
                add("warp_private_key", R.string.warp_private_key, 0)
                add("warp_public_key", R.string.warp_public_key, 0)
                add("warp_ifc_addrs", R.string.warp_interface_addresses, 0)
                add("warp_reserved", R.string.warp_reserved, 0)
                add("warp_masque_ep", R.string.warp_endpoint, 0)
                add("warp_masque_private_key", R.string.warp_private_key, 0)
                add("warp_masque_peer_public_key", R.string.warp_peer_public_key, 0)
                add("warp_masque_ifc_addrs", R.string.warp_interface_addresses, 0)
                add("warp_masque_sni", R.string.sni, 0)
                add("warp_masque_http_mode", R.string.warp_http_version, 0)
                add("warpGenerate", R.string.warp_generate, R.string.warp_generate_sum)
                add("warp_api_hosts", R.string.warp_api_hosts, 0)
            }
            screen(XRAY_GEO_SETTINGS, R.string.settings_general) {
                add("xrayGeoSource", R.string.xray_geo_source, 0)
                add("xray_geoip_url", R.string.xray_geo_geoip_url, 0)
                add("xray_geosite_url", R.string.xray_geo_geosite_url, 0)
                add(
                    "xrayGeoDownload",
                    R.string.xray_geo_download_now,
                    R.string.xray_geo_download_now_sum,
                )
            }
        }
    }

    private class ScreenScope(val list: MutableList<Entry>, val screen: String, val section: Int) {
        fun add(key: String, title: Int, summary: Int = 0) {
            list += Entry(key, title, summary, screen, section)
        }
    }

    private inline fun MutableList<Entry>.screen(
        screen: String,
        section: Int,
        block: ScreenScope.() -> Unit,
    ) {
        ScreenScope(this, screen, section).block()
    }
}
