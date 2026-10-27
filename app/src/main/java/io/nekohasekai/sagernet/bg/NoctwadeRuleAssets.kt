package io.nekohasekai.sagernet.bg

import android.content.Context
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.route.RuleSets
import java.io.File

/** Install the APK's fixed routing snapshot once per app revision, before starting the core. */
internal object NoctwadeRuleAssets {
    fun install(context: Context) {
        val dir = File(context.filesDir, "${CoreRuntime.WORKING_DIR}/noctwade-rule-sets")
        val marker = File(dir, "snapshot-version")
        if (
            marker.isFile &&
                marker.readText() == BuildConfig.VERSION_CODE.toString() &&
                RuleSets.bundledFiles.values.all { File(dir, it).isFile }
        )
            return
        dir.mkdirs()
        for (name in RuleSets.bundledFiles.values.toSet()) {
            val target = File(dir, name)
            val temp = File(dir, "$name.tmp")
            context.assets.open("noctwade-rule-sets/$name").use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
            check(temp.renameTo(target)) { "Cannot install routing snapshot: $name" }
        }
        marker.writeText(BuildConfig.VERSION_CODE.toString())
    }
}
