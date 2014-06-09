package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._

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

  protected def anchorForNewDeclaration(expr: ScExpression, occurrences: Array[TextRange], aClass: ScTemplateDefinition): PsiElement = {
    val commonParent = ScalaRefactoringUtil.commonParent(aClass.getContainingFile, occurrences: _*)
    val firstOccOffset = occurrences.map(_.getStartOffset).min
    val anchor = ScalaRefactoringUtil.statementsAndMembersInClass(aClass).find(_.getTextRange.getEndOffset >= firstOccOffset)
    anchor.getOrElse {
      if (PsiTreeUtil.isAncestor(aClass.extendsBlock.templateBody.orNull, commonParent, false)) null
      else {
        aClass.extendsBlock match {
          case ScExtendsBlock.EarlyDefinitions(earlyDef) => earlyDef.lastChild.orNull
          case extBl => extBl.templateParents.orNull
        }
      }
    }
  }
}

object ScalaIntroduceFieldHandlerBase {

  def canBeInitializedInDeclaration(expr: ScExpression, aClass: ScTemplateDefinition): Boolean = {
    val stmtsAndMmbrs = ScalaRefactoringUtil.statementsAndMembersInClass(aClass)
    (Iterator(expr) ++ expr.parents)
            .find(stmtsAndMmbrs.contains(_))
            .forall(ScalaRefactoringUtil.checkForwardReferences(expr, _))
  }

  def canBeInitInLocalScope[T <: PsiElement](ifc: IntroduceFieldContext[T], replaceAll: Boolean): Boolean = {
    val occurrences = if (replaceAll) ifc.occurrences else Array(ifc.element.getTextRange)
    val parExpr: ScExpression = ScalaRefactoringUtil.findParentExpr(ScalaRefactoringUtil.commonParent(ifc.file, occurrences: _*))
    val container = ScalaRefactoringUtil.container(parExpr, ifc.file)
    val stmtsAndMmbrs = ScalaRefactoringUtil.statementsAndMembersInClass(ifc.aClass)
    val containerIsLocal = (Iterator(container) ++ new ParentsIterator(container)).exists(stmtsAndMmbrs.contains(_))
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
    val container: PsiElement = ScalaRefactoringUtil.container(parExpr, file)
    val needBraces = !parExpr.isInstanceOf[ScBlock] && ScalaRefactoringUtil.needBraces(parExpr, ScalaRefactoringUtil.nextParent(parExpr, file))
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