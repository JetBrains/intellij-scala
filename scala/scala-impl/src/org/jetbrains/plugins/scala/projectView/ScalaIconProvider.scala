package org.jetbrains.plugins.scala
package projectView

import com.intellij.ide.IconProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class ScalaIconProvider extends IconProvider {

  override def getIcon(element: PsiElement, flags: Int): Icon = element match {
    case file: ScalaFile =>
      ProgressManager.checkCanceled()
      getIcon(file, flags)
    case _ => null
  }

  import icons.Icons._

  private def getIcon(file: ScalaFile, flags: Int) = file match {
    case _ if file.isScriptFile => SCRIPT_FILE_LOGO
    case SingularDefinition(definition) => definition.getIcon(flags)
    case ClassAndCompanionObject(clazz, _) =>
      clazz.decorate(
        if (clazz.hasAbstractModifier) ABSTRACT_CLASS_AND_OBJECT else CLASS_AND_OBJECT,
        flags
      )
    case TraitAndCompanionObject(_, _) => TRAIT_AND_OBJECT
    case _ => file.getFileType.getIcon
  }

}