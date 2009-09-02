package org.jetbrains.plugins.scala
package components

import com.intellij.psi._
import icons.Icons
import javax.swing.Icon
import com.intellij.ide.IconProvider
import lang.psi.api.ScalaFile
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

class ScalaIconProvider extends IconProvider {
  @Nullable
  override def getIcon(element: PsiElement, flags: Int): Icon = {

    if (element.isInstanceOf[ScalaFile]) {
      val file = element.asInstanceOf[ScalaFile]
      if (file.isScriptFile) return Icons.SCRIPT_FILE_LOGO
      val name = file.getVirtualFile.getNameWithoutExtension
      val defs = file.typeDefinitions
      for (val clazz <- defs) {
        if (name.equals(clazz.getName)) return clazz.getIcon(flags)
      }
      if (!defs.isEmpty) return defs(0).getIcon(flags)
    }
    null
  }

  def getComponentName = "Scala Icon Provider"

  def initComponent = {
  }

  def disposeComponent = {
  }
}