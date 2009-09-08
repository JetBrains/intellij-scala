package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.usageView.UsageInfo
import java.lang.String
import com.intellij.refactoring.rename.{RenamePsiElementProcessor, RenameJavaMemberProcessor}
import com.intellij.openapi.editor.Editor

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.09.2009
 */

class RenameFakeMethodProcessor extends RenamePsiElementProcessor {
  def canProcessElement(element: PsiElement): Boolean = element.isInstanceOf[FakePsiMethod]

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    element match {
      case f: FakePsiMethod => {
        f.navElement
      }
      case _ => element
    }
  }
}