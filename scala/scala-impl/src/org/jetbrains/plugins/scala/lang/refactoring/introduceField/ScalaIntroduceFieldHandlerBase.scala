package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceField

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._

/**
 * Nikolay.Tropin
 * 6/28/13
 */
abstract class ScalaIntroduceFieldHandlerBase extends ScalaRefactoringActionHandler {

  val REFACTORING_NAME: String = ScalaBundle.message("introduce.field.title")

  protected def isSuitableClass(elem: PsiElement, clazz: ScTemplateDefinition): Boolean

  def afterClassChoosing[T <: PsiElement](elem: T, types: Array[ScType], project: Project, editor: Editor, file: PsiFile, @Nls title: String)
                                         (action: IntroduceFieldContext[T] => Unit): Unit = {
    try {
      val classes = ScalaPsiUtil.getParents(elem, file).collect {
        case t: ScTemplateDefinition if isSuitableClass(elem, t) => t
      }.toArray[PsiClass]
      classes.length match {
        case 0 =>
        case 1 => action(new IntroduceFieldContext[T](project, editor, file, elem, types, classes(0).asInstanceOf[ScTemplateDefinition]))
        case _ =>
          val selection = classes(0)
          val processor = new PsiElementProcessor[PsiClass] {
            override def execute(aClass: PsiClass): Boolean = {
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
      case _: IntroduceException =>
    }
  }

  protected def anchorForNewDeclaration(expr: ScExpression, occurrences: Seq[TextRange], aClass: ScTemplateDefinition): PsiElement = {
    val firstOccOffset = occurrences.map(_.getStartOffset).min
    val anchor = statementsAndMembersInClass(aClass).find(_.getTextRange.getEndOffset >= firstOccOffset)
    anchor.getOrElse {
      if (PsiTreeUtil.isAncestor(aClass.extendsBlock.templateBody.orNull, commonParent(aClass.getContainingFile, occurrences), false)) null
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
    val stmtsAndMmbrs = statementsAndMembersInClass(aClass)
    expr.withParentsInFile
            .find(stmtsAndMmbrs.contains(_))
      .forall(checkForwardReferences(expr, _))
  }

  def canBeInitInLocalScope[T <: PsiElement](ifc: IntroduceFieldContext[T], replaceAll: Boolean): Boolean = {
    val occurrences = if (replaceAll) ifc.occurrences else Seq(ifc.element.getTextRange)
    val parExpr: ScExpression = findParentExpr(commonParent(ifc.file, occurrences))
    val stmtsAndMmbrs = statementsAndMembersInClass(ifc.aClass)
    val containerIsLocal = container(parExpr).getOrElse(ifc.file)
      .withParentsInFile
      .exists(stmtsAndMmbrs.contains(_))

    if (!containerIsLocal) false
    else {
      ifc.element match {
        case expr: ScExpression => checkForwardReferences(expr, parExpr)
        case _ => false
      }
    }
  }

  def anchorForInitializer(occurrences: Seq[TextRange], file: PsiFile): Option[PsiElement] = {
    var firstRange = occurrences.head

    val parExpr = findParentExpr(commonParent(file, occurrences))
    if (parExpr == null) return None

    val isNotBlock = !parExpr.isInstanceOf[ScBlock]
    val parent =
      if (isNotBlock && needBraces(parExpr, nextParent(parExpr, file))) {
        firstRange = firstRange.shiftRight(1)
        parExpr.replaceExpression(createExpressionFromText(s"{${parExpr.getText}}")(file.getManager),
          removeParenthesis = false)
      } else container(parExpr).getOrElse(file)
    if (parent == null) None
    else parent.getChildren.find(_.getTextRange.contains(firstRange))
  }

}