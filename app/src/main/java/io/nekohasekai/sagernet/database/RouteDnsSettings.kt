package io.nekohasekai.sagernet.database

import io.nekohasekai.sagernet.route.RouteDns

/** The DNS settings a config is generated with: one global set shared by every routing profile. */
object RouteDnsSettings {

    /** The DNS settings of Routing › DNS. */
    fun global(): RouteDns = RouteDns(
        remoteDns = DataStore.remoteDns,
        remoteDnsDisableIpv6 = DataStore.remoteDnsDisableIpv6,
        directDns = DataStore.directDns,
        directDnsDisableIpv6 = DataStore.directDnsDisableIpv6,
        dnsFinalOut = DataStore.dnsFinalOut,
        enableDnsRouting = DataStore.enableDnsRouting,
        fakeDns = DataStore.fakedns,
        fakeIpDisableIpv6 = DataStore.fakeipDisableIpv6,
    )
}
