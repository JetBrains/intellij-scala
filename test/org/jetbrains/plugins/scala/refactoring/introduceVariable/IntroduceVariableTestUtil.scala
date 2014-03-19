package org.jetbrains.plugins.scala
package refactoring.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import lang.psi.api.expr._
import lang.psi.ScalaPsiUtil
import com.intellij.psi.PsiElement
import lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.ScalaFile
import lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler
import lang.psi.types.ScType
import lang.refactoring.util.{ScalaVariableValidator, ScalaRefactoringUtil}

/**
 * @author Alexander Podkhalyuzin
 * @date 05.04.2009
 */

object IntroduceVariableTestUtil {
  def extract1[T,U](x: (T, U)): T = x._1
  def extract2[T,U](x: (T, U)): U = x._2

  def getValidator(project: Project, editor: Editor, file: ScalaFile, startOffset: Int, endOffset: Int): ScalaVariableValidator = {
    val (expr: ScExpression, _) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).get

    val fileEncloser = if (file.isScriptFile()) file
    else {
      var res: PsiElement = file.findElementAt(startOffset)
      while (res.getParent != null && !res.getParent.isInstanceOf[ScTemplateBody]) res = res.getParent
      if (res != null) res
      else {
        for (child <- file.getChildren) {
          val textRange: TextRange = child.getTextRange
          if (textRange.contains(startOffset)) res = child
        }
        res
      }
    }
    val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), fileEncloser)
    // Getting settings
    val elemSeq = (for (occurence <- occurrences) yield file.findElementAt(occurence.getStartOffset)).toSeq ++
            (for (occurence <- occurrences) yield file.findElementAt(occurence.getEndOffset - 1)).toSeq
    val commonParent: PsiElement = PsiTreeUtil.findCommonParent(elemSeq: _*)
    val container: PsiElement = ScalaPsiUtil.getParentOfType(commonParent, occurrences.length == 1, classOf[ScalaFile], classOf[ScBlock],
      classOf[ScTemplateBody])
    val commonParentOne = PsiTreeUtil.findCommonParent(file.findElementAt(startOffset), file.findElementAt(endOffset - 1))
    val containerOne = ScalaPsiUtil.getParentOfType(commonParentOne, occurrences.length == 1, classOf[ScalaFile], classOf[ScBlock],
      classOf[ScTemplateBody])

    new ScalaVariableValidator(new ScalaIntroduceVariableHandler, project, expr, occurrences.isEmpty, container, containerOne)
  }
}