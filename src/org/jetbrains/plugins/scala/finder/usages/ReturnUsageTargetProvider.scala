package org.jetbrains.plugins.scala.finder.usages

import com.intellij.usages.{UsageTarget, UsageTargetProvider}
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.12.2009
 */

class ReturnUsageTargetProvider extends UsageTargetProvider {
  def getTargets(psiElement: PsiElement): Array[UsageTarget] = {
    null
  }

  def getTargets(editor: Editor, file: PsiFile): Array[UsageTarget] = {
    val element: PsiElement = file.findElementAt(editor.getCaretModel.getOffset)
    if (element.getNode.getElementType == ScalaTokenTypes.kRETURN) {
      val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition])
      if (fun == null) return null
      else fun.getReturnUsages.map(new PsiElement2UsageTargetAdapter(_))
    } else null
  }
}