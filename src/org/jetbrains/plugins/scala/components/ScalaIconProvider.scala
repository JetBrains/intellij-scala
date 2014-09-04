package org.jetbrains.plugins.scala
package components

import javax.swing.Icon

import com.intellij.ide.IconProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaIconProvider extends IconProvider {
  @Nullable
  override def getIcon(element: PsiElement, flags: Int): Icon = {
    ProgressManager.checkCanceled()
    element match {
      case null => null
      case file: ScalaFile =>
        if (file.isWorksheetFile) return Icons.WORKSHEET_LOGO
        if (file.isScriptFile()) return Icons.SCRIPT_FILE_LOGO
        if (file.getVirtualFile == null) return Icons.SCRIPT_FILE_LOGO
        val name = file.getVirtualFile.getNameWithoutExtension
        val defs = file.typeDefinitions
        val clazzIterator = defs.iterator
        while (clazzIterator.hasNext) {
          val clazz = clazzIterator.next()
          if (name.equals(clazz.name)) return clazz.getIcon(flags)
        }
        if (!defs.isEmpty) return defs(0).getIcon(flags)
      case _ =>
    }
    null
  }
}