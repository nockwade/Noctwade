package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R

/** QUERY_ALL_PACKAGES is not a runtime permission. Request an OEM's installed-apps permission only when it exists. */
internal class InstalledAppsAccess(private val host:Fragment,private val refresh:()->Unit){
 private val permission=host.registerForActivityResult(ActivityResultContracts.RequestPermission()){refresh()}
 private val settings=host.registerForActivityResult(ActivityResultContracts.StartActivityForResult()){refresh()}
 fun deniedPermission():String? {
  val context=host.requireContext()
  val name="com.android.permission.GET_INSTALLED_APPS"
  val info=try{context.packageManager.getPermissionInfo(name,0)}catch(_:PackageManager.NameNotFoundException){return null}
  return name.takeIf{info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE==PermissionInfo.PROTECTION_DANGEROUS && ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED}
 }
 fun requestIfNeeded():Boolean {val name=deniedPermission()?:return false;permission.launch(name);return true}
 fun explainSettings(){
  MaterialAlertDialogBuilder(host.requireContext()).setTitle(R.string.nw_apps_access_title).setMessage(R.string.nw_apps_access_hint)
   .setPositiveButton(R.string.open_app_settings){_,_->openSettings()}.setNegativeButton(android.R.string.cancel,null).show()
 }
 fun openSettings(){
  val name=deniedPermission()
  if(name!=null && host.shouldShowRequestPermissionRationale(name)){
   MaterialAlertDialogBuilder(host.requireContext()).setTitle(R.string.nw_apps_access_title).setMessage(R.string.nw_apps_access_hint).setPositiveButton(R.string.nw_apps_allow){_,_->permission.launch(name)}.setNegativeButton(android.R.string.cancel,null).show()
  }else{
   settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",host.requireContext().packageName,null)))
  }
 }
}
