package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.AbstractTypeAnnotationIntention._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScReferencePattern, ScTypedPatternLike, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScTypedExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.Context
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkIntention

abstract class AbstractTypeAnnotationIntention extends PsiElementBaseIntentionAction {

  import AbstractTypeAnnotationIntention.complete

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    adjustElementAtOffset(element, editor) match {
      case element: PsiElement if checkIntention(this, element) =>
        complete(element, descriptionStrategy)
      case _ => false
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    complete(adjustElementAtOffset(element, editor), invocationStrategy(Option(editor)))

  protected def descriptionStrategy: Strategy

  protected def invocationStrategy(maybeEditor: Option[Editor]): Strategy
}

object AbstractTypeAnnotationIntention {
  private def adjustElementAtOffset(element: PsiElement, editor: Editor): PsiElement = {
    val caretOffset = editor.getCaretModel.getOffset
    val adjusted1 = ScalaPsiUtil.adjustElementAtOffset(element, caretOffset)

    // Handle some cases when the caret is located after identifier/underscore
    // and before some "separator" tokens: (dot, coma, parentheses).
    // In this case this next element will be selected, and we need to adjust it to the previous leaf.
    // Example 1: Seq(1, 2).map(_<caret>.toString)
    // Example 2: Seq(1, 2).map((_: Int<caret>).toString)
    // Example 3: case Some(SomeCaseClass(_<caret>, bar, _))
    val adjusted2 = adjusted1.elementType match {
      case ScalaTokenTypes.tCOMMA | ScalaTokenTypes.tDOT | ScalaTokenTypes.tRPARENTHESIS =>
        adjusted1.getPrevNonEmptyLeaf
      case _ =>
        adjusted1
    }
    adjusted2
  }

  def functionParent(element: PsiElement): Option[ScFunctionDefinition] =
    for {
      function <- element.parentsInFile.findByType[ScFunctionDefinition]
      if function.hasAssign
      body <- function.body
      if !body.isAncestorOf(element)
    } yield function

  def valueParent(element: PsiElement): Option[ScPatternDefinition] =
    for {
      value <- element.parentsInFile.findByType[ScPatternDefinition]
      if value.expr.forall(!_.isAncestorOf(element))
      if value.pList.simplePatterns
      if value.bindings.size == 1
    } yield value

  def variableParent(element: PsiElement): Option[ScVariableDefinition] =
    for {
      variable <- element.parentsInFile.findByType[ScVariableDefinition]
      if variable.expr.forall(!_.isAncestorOf(element))
      if variable.pList.simplePatterns
      if variable.bindings.size == 1
    } yield variable

  private[types] def underscoreSectionParent(element: PsiElement): Option[ScUnderscoreSection] =
    element.withParentsInFile.collectFirst {
      case underscore: ScUnderscoreSection => underscore
      case (_: ScTypedExpression) & FirstChild(underscore: ScUnderscoreSection) => underscore
    }

  def complete(element: PsiElement, strategy: Strategy): Boolean = {
    implicit val context: Context = Context(element)

    functionParent(element).foreach { function =>
      return function.returnTypeElement match {
        case Some(typeElement) =>
          strategy.functionWithType(function, typeElement)
        case _ =>
          strategy.functionWithoutType(function)
      }
    }

    valueParent(element).foreach { value =>
      return value.typeElement match {
        case Some(typeElement) =>
          strategy.valueWithType(value, typeElement)
        case _ =>
          strategy.valueWithoutType(value)
      }
    }

    variableParent(element).foreach { variable =>
      return variable.typeElement match {
        case Some(typeElement) =>
          strategy.variableWithType(variable, typeElement)
        case _ =>
          strategy.variableWithoutType(variable)
      }
    }

    val underscoreSection = underscoreSectionParent(element)
    underscoreSection.foreach { underscore =>
      return if (underscore.getParent.is[ScTypedExpression])
        strategy.underscoreSectionWithType(underscore)
      else
        strategy.underscoreSectionWithoutType(underscore)
    }

    for {
      param <- element.parentsInFile.findByType[ScParameter]
    } {
      param.parentsInFile.findByType[ScFunctionExpr] match {
        case Some(func) =>
          if (param.typeElement.isDefined) {
            return strategy.parameterWithType(param)
          } else {
            val index = func.parameters.indexOf(param)
            func.expectedType() match {
              case Some(FunctionType(_, params)) =>
                if (index >= 0 && index < params.length) {
                  return strategy.parameterWithoutType(param)
                }
              case _ =>
            }
          }
        case _ =>
      }
    }

    for (pattern <- element.parentsInFile.findByType[ScPattern]) {
      pattern match {
        case p@ScTypedPatternLike(_) =>
          return strategy.patternWithType(p)
        case p: ScReferencePattern =>
          p.getParent match {
            case typed: ScTypedPatternLike =>
              return strategy.patternWithType(typed)
            case _ =>
              return strategy.patternWithoutType(p)
          }
        case w: ScWildcardPattern =>
          w.getParent match {
            case typed: ScTypedPatternLike =>
              return strategy.patternWithType(typed)
            case _ =>
              return strategy.patternWithoutType(w)
          }
        case _ =>
      }
    }
    false
  }
}
