package org.jetbrains.plugins.scala.projectView

import javax.swing.Icon

import com.intellij.ide.IconProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaIconProvider extends IconProvider {
  override def getIcon(element: PsiElement, flags: Int): Icon = {
    ProgressManager.checkCanceled()

    val icon = Some(element) collect {
      case file: ScalaFile if file.isWorksheetFile => Icons.WORKSHEET_LOGO
      case file: ScalaFile if file.isScriptFile || file.getVirtualFile == null => Icons.SCRIPT_FILE_LOGO
      case file: ScalaFile if file.getFileType != ScalaFileType.INSTANCE => file.getFileType.getIcon
      case SingularDefinition(definition) => definition.getIcon(flags)
      case ClassAndCompanionObject(classDefinition, _) =>
        if (classDefinition.hasAbstractModifier) Icons.ABSTRACT_CLASS_AND_OBJECT else Icons.CLASS_AND_OBJECT
      case TraitAndCompanionObject(_, _) => Icons.TRAIT_AND_OBJECT
      case _: ScalaFile => Icons.FILE
    }

    icon.orNull
  }
}