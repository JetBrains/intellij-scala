package org.jetbrains.plugins.scala.lang.refactoring.introduceField

import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.ide.util.PsiClassRenderingInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.navigation.DelegatingPsiTargetPresentationRenderer
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.ScalaRefactoringActionHandler
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._

import scala.collection.immutable.ArraySeq

abstract class ScalaIntroduceFieldHandlerBase extends ScalaRefactoringActionHandler {

  val REFACTORING_NAME: String = ScalaBundle.message("introduce.field.title")

  protected def isSuitableClass(elem: PsiElement, clazz: ScTemplateDefinition): Boolean

  def afterClassChoosing[T <: PsiElement](elem: T, types: ArraySeq[ScType], project: Project, editor: Editor, file: PsiFile, @Nls title: String)
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
          val processor: PsiElementProcessor[PsiClass] = { aClass =>
            action(new IntroduceFieldContext[T](project, editor, file, elem, types, aClass.asInstanceOf[ScTemplateDefinition]))
            false
          }

          new PsiTargetNavigator(classes)
            .selection(selection)
            .presentationProvider(new DelegatingPsiTargetPresentationRenderer(PsiClassRenderingInfo.INSTANCE))
            .createPopup(project, title, processor)
            .showInBestPositionFor(editor)
      }
    }
    catch {
      case _: IntroduceException =>
    }
  }

  protected def anchorForNewDeclaration(occurrences: Seq[TextRange], aClass: ScTemplateDefinition): PsiElement = {
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

    val isNotBlock = !parExpr.is[ScBlock]
    val parent =
      if (isNotBlock && needBraces(parExpr, nextParent(parExpr, file))) {
        firstRange = firstRange.shiftRight(1)
        parExpr.replaceExpression(createExpressionFromText(s"{${parExpr.getText}}", parExpr)(file.getManager),
          removeParenthesis = false)
      } else container(parExpr).getOrElse(file)
    if (parent == null) None
    else parent.getChildren.find(_.getTextRange.contains(firstRange))
  }

}
