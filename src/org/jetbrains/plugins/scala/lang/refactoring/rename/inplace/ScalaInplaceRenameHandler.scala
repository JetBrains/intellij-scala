package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.{PsiElementRenameHandler, RenamePsiElementProcessor}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, LightScalaMethod}
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor

/**
 * Nikolay.Tropin
 * 1/20/14
 */
trait ScalaInplaceRenameHandler {

  def renameProcessor(element: PsiElement): RenamePsiElementProcessor = {
    val isScalaElement = element match {
      case null => false
      case _: LightScalaMethod | _: PsiClassWrapper => true
      case _  => element.getLanguage.isInstanceOf[ScalaLanguage]
    }
    val processor = if (isScalaElement) RenamePsiElementProcessor.forElement(element) else null
    if (processor != RenamePsiElementProcessor.DEFAULT) processor else null
  }

  protected def doDialogRename(element: PsiElement, project: Project, nameSuggestionContext: PsiElement, editor: Editor): Unit = {
    PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor)
  }
}
