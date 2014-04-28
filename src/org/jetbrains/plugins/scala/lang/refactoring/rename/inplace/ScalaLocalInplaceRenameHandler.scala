package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.refactoring.rename.inplace.{VariableInplaceRenamer, VariableInplaceRenameHandler}
import com.intellij.psi.{PsiNamedElement, PsiFile, PsiElement}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.LocalSearchScope

/**
 * Nikolay.Tropin
 * 1/20/14
 */
class ScalaLocalInplaceRenameHandler extends VariableInplaceRenameHandler with ScalaInplaceRenameHandler {

  override def isAvailable(element: PsiElement, editor: Editor, file: PsiFile): Boolean = {
    val processor = renameProcessor(element)
    editor.getSettings.isVariableInplaceRenameEnabled && processor != null && processor.canProcessElement(element) &&
            element.getUseScope.isInstanceOf[LocalSearchScope]
  }

  override def createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer = {
    elementToRename match {
      case named: PsiNamedElement => new ScalaLocalInplaceRenamer(named, editor)
      case _ => throw new IllegalArgumentException(s"Cannot rename element: \n${elementToRename.getText}")
    }
  }

}
