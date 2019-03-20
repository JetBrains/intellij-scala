package org.jetbrains.plugins.scala
package projectView

import com.intellij.ide.IconProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaIconProvider extends IconProvider {
  override def getIcon(element: PsiElement, flags: Int): Icon = {
    ProgressManager.checkCanceled()

    import icons.Icons._
    val icon = Some(element) collect {
      case file: ScalaFile if file.isWorksheetFile => worksheet.WorksheetFileType.getIcon
      case file: ScalaFile if file.isScriptFile || file.getVirtualFile == null => SCRIPT_FILE_LOGO
      case file: ScalaFile if file.getFileType != ScalaFileType.INSTANCE => file.getFileType.getIcon
      case SingularDefinition(definition) => definition.getIcon(flags)
      case ClassAndCompanionObject(classDefinition, _) =>
        classDefinition.decorate(
          if (classDefinition.hasAbstractModifier) ABSTRACT_CLASS_AND_OBJECT else CLASS_AND_OBJECT,
          flags
        )
      case TraitAndCompanionObject(_, _) => TRAIT_AND_OBJECT
      case _: ScalaFile => FILE
    }

    icon.orNull
  }
}