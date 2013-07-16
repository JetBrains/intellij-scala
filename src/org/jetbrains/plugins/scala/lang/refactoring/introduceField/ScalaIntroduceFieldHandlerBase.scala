package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.psi.search.PsiElementProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 6/28/13
 */
abstract class ScalaIntroduceFieldHandlerBase extends RefactoringActionHandler{

  val REFACTORING_NAME = ScalaBundle.message("introduce.field.title")

  protected def isSuitableClass(elem: PsiElement, clazz: ScTemplateDefinition): Boolean

  def afterClassChoosing[T <: PsiElement](elem: T, types: Array[ScType], project: Project, editor: Editor, file: PsiFile, title: String)
                                         (action: IntroduceFieldContext[T] => Unit) {
    try {
      val classes = ScalaPsiUtil.getParents(elem, file).collect {
        case t: ScTemplateDefinition if isSuitableClass(elem, t) => t
      }.toArray[PsiClass]
      classes.size match {
        case 0 =>
        case 1 => action(new IntroduceFieldContext[T](project, editor, file, elem, types, classes(0).asInstanceOf[ScTemplateDefinition]))
        case _ =>
          val selection = classes(0)
          val processor = new PsiElementProcessor[PsiClass] {
            def execute(aClass: PsiClass): Boolean = {
              action(new IntroduceFieldContext[T](project, editor, file, elem, types, aClass.asInstanceOf[ScTemplateDefinition]))
              false
            }
          }
          NavigationUtil.getPsiElementPopup(classes, new PsiClassListCellRenderer() {
            override def getElementText(element: PsiClass): String = super.getElementText(element).replace("$", "")
          }, title, processor, selection).showInBestPositionFor(editor)
      }
    }
    catch {
      case _: IntroduceException => return
    }
  }

  protected def anchorForNewDeclaration(expr: ScExpression, occurrences: Array[PsiElement], aClass: ScTemplateDefinition): PsiElement = {
    val firstOccOffset = occurrences.map(_.getTextRange.getStartOffset).min
    ScalaRefactoringUtil.statementsAndMembersInClass(aClass).find(_.getTextRange.getEndOffset > firstOccOffset).get
  }
}

object ScalaIntroduceFieldHandlerBase {

  def canBeInitializedInDeclaration(expr: ScExpression, aClass: ScTemplateDefinition): Boolean = {
    var result = true
    ScalaRefactoringUtil.statementsAndMembersInClass(aClass).find(PsiTreeUtil.isAncestor(_, expr, false)).foreach { ancestor =>
      val checkRef = ScalaRefactoringUtil.checkForwardReferences(expr, ancestor)
      result &= checkRef
    }
    result
  }

  def canBeInitInLocalScope[T <: PsiElement](ifc: IntroduceFieldContext[T], replaceAll: Boolean): Boolean = {
    val occurrences = if (replaceAll) ifc.occurrences else Array(ifc.element.getTextRange)
    val parExpr: ScExpression = ScalaRefactoringUtil.findParentExpr(ScalaRefactoringUtil.commonParent(ifc.file, occurrences: _*))
    val container = ScalaRefactoringUtil.container(parExpr, ifc.file, strict = false)
    val containerIsLocal =
      ScalaRefactoringUtil.statementsAndMembersInClass(ifc.aClass).exists(PsiTreeUtil.isAncestor(_, container, /*strict =*/false))
    if (!containerIsLocal) false
    else {
      ifc.element match {
        case expr: ScExpression => checkForwardReferences(expr, parExpr)
        case _ => false
      }
    }
  }

  def anchorForInitializer(occurences: Array[TextRange], file: PsiFile): Option[PsiElement] = {
    var firstRange = occurences(0)
    val commonParent = ScalaRefactoringUtil.commonParent(file, occurences: _*)

    val parExpr = ScalaRefactoringUtil.findParentExpr(commonParent)
    if (parExpr == null) return None
    val container: PsiElement = ScalaRefactoringUtil.container(parExpr, file, strict = occurences.length == 1)
    val needBraces = ScalaRefactoringUtil.needNewBraces(parExpr, ScalaRefactoringUtil.previous(parExpr, file))
    val parent =
      if (needBraces) {
        firstRange = firstRange.shiftRight(1)
        parExpr.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("{" + parExpr.getText + "}", file.getManager),
          removeParenthesis = false)
      } else container
    if (parent == null) None
    else parent.getChildren.find(_.getTextRange.contains(firstRange))
  }

}