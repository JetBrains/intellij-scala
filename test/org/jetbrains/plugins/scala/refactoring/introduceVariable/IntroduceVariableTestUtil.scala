package org.jetbrains.plugins.scala
package refactoring.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.refactoring.util._

/**
 * @author Alexander Podkhalyuzin
 * @date 05.04.2009
 */

object IntroduceVariableTestUtil {
  def extract1[T, U](x: (T, U)): T = x._1

  def extract2[T, U](x: (T, U)): U = x._2

  def getContainerOne(startOffset: Int, endOffset: Int, file: ScalaFile, occLength: Int) = {
    val commonParentOne = PsiTreeUtil.findCommonParent(file.findElementAt(startOffset), file.findElementAt(endOffset - 1))
    ScalaPsiUtil.getParentOfType(commonParentOne, occLength == 1, classOf[ScalaFile], classOf[ScBlock],
      classOf[ScTemplateBody])
  }

  def getValidator(project: Project, editor: Editor, file: ScalaFile, startOffset: Int, endOffset: Int): ScalaValidator = {
    PsiTreeUtil.getParentOfType(file.findElementAt(startOffset), classOf[ScExpression], classOf[ScTypeElement]) match {
      case x: ScExpression => getVariableValidator(project, editor, file, startOffset, endOffset)
      case x: ScTypeElement => getTypeValidator(project, editor, file, startOffset, endOffset)
      case _ => null
    }
  }

  def getVariableValidator(project: Project, editor: Editor, file: ScalaFile, startOffset: Int, endOffset: Int): ScalaVariableValidator = {
    val (expr: ScExpression, _) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).get

    val fileEncloser = ScalaRefactoringUtil.fileEncloser(startOffset, file)
    val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), fileEncloser)

    val container: PsiElement = ScalaRefactoringUtil.enclosingContainer(ScalaRefactoringUtil.commonParent(file, occurrences: _*))
    val containerOne = getContainerOne(startOffset, endOffset, file, occurrences.length)
    new ScalaVariableValidator(expr, occurrences.isEmpty, container, containerOne)
  }

  def getTypeValidator(project: Project, editor: Editor, file: ScalaFile, startOffset: Int, endOffset: Int): ScalaTypeValidator = {
    val typeElement = ScalaRefactoringUtil.getTypeElement(project, editor, file, startOffset, endOffset).get

    val fileEncloser = ScalaRefactoringUtil.fileEncloser(startOffset, file)
    val occurrences = ScalaRefactoringUtil.getTypeElementOccurrences(typeElement, fileEncloser)
    val container = ScalaRefactoringUtil.enclosingContainer(PsiTreeUtil.findCommonParent(occurrences: _*))

    val containerOne = getContainerOne(startOffset, endOffset, file, occurrences.length)
    new ScalaTypeValidator(typeElement, occurrences.isEmpty, container, containerOne)
  }
}