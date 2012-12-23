package org.jetbrains.plugins.scala
package compiler

import javax.swing.JList
import com.intellij.openapi.projectRoots.{SdkType, Sdk}
import com.intellij.ui.ListCellRendererWrapper

/**
 * @author Pavel Fatin
 */
class SdkRenderer extends ListCellRendererWrapper[Sdk] {
  def customize(list: JList, value: Sdk, index: Int, selected: Boolean, hasFocus: Boolean) {
    val (icon, text) = value match {
      case sdk: Sdk =>
        val icon = sdk.getSdkType.asInstanceOf[SdkType].getIcon

        val name = sdk.getName
        val version = sdk.getVersionString
        val text = if (version == null) name else "%s %s".format(name, version)

        (icon, text)
      case _ => (null, "<No SDK>")
    }

    setIcon(icon)
    setText(text)
  }
}
