package org.jetbrains.plugins.scala.projectView

import javax.swing.Icon

import com.intellij.ide.IconProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaIconProvider extends IconProvider {
  override def getIcon(element: PsiElement, flags: Int): Icon = {
    ProgressManager.checkCanceled()

    val icon = Some(element) collect {
      case SingularDefinition(definition) => definition.getIcon(flags)
      case ClassAndCompanionObject(_, _) => Icons.CLASS_AND_OBJECT
      case TraitAndCompanionObject(_, _) => Icons.TRAIT_AND_OBJECT
      case file: ScalaFile =>
        if (file.isWorksheetFile) Icons.WORKSHEET_LOGO
        else if (file.isScriptFile || file.getVirtualFile == null) Icons.SCRIPT_FILE_LOGO
        else Icons.FILE
    }

    icon.orNull
  }
}