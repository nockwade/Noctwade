package io.nekohasekai.sagernet.ui
import android.os.Bundle
import io.nekohasekai.sagernet.R
/** Standalone entry point shares the same application routing screen as the routing tab. */
class AppManagerActivity:ThemedActivity(R.layout.layout_config_settings){
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  val toolbar=findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
  toolbar.setTitle(R.string.proxied_apps);toolbar.setNavigationIcon(R.drawable.nw_back);toolbar.setNavigationOnClickListener{finish()}
  if(state==null)supportFragmentManager.beginTransaction().replace(R.id.settings,AppRoutingFragment()).commit()
 }
}
