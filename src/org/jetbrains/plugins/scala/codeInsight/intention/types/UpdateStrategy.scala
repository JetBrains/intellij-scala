package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScType}

/**
 * Pavel.Fatin, 28.04.2010
 */

object AddOrRemoveStrategy extends UpdateStrategy

object AddOnlyStrategy extends UpdateStrategy {
  override def removeFromFunction(function: ScFunctionDefinition): Unit = {}
  override def removeFromParameter(param: ScParameter): Unit = {}
  override def removeFromPattern(pattern: ScTypedPattern): Unit = {}
  override def removeFromValue(value: ScPatternDefinition): Unit = {}
  override def removeFromVariable(variable: ScVariableDefinition): Unit = {}
}

abstract class UpdateStrategy extends Strategy {
  def addToFunction(function: ScFunctionDefinition) {
    function.returnType.foreach {
      addTypeAnnotation(_, function, function.paramClauses)
    }
  }

  def removeFromFunction(function: ScFunctionDefinition) {
    function.returnTypeElement.foreach(removeTypeAnnotation)
  }

  def addToValue(value: ScPatternDefinition) {
    value.getType(TypingContext.empty).toOption.foreach {
      addTypeAnnotation(_, value, value.pList)
    }
  }

  def removeFromValue(value: ScPatternDefinition) {
    value.typeElement.foreach(removeTypeAnnotation)
  }

  def addToVariable(variable: ScVariableDefinition) {
    variable.getType(TypingContext.empty).toOption.foreach {
      addTypeAnnotation(_, variable, variable.pList)
    }
  }

  def removeFromVariable(variable: ScVariableDefinition) {
    variable.typeElement.foreach(removeTypeAnnotation)
  }

  def addToPattern(pattern: ScBindingPattern) {
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }
  }

  def addToWildcardPattern(pattern: ScWildcardPattern) {
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }
  }

  def removeFromPattern(pattern: ScTypedPattern) {
    val newPattern = ScalaPsiElementFactory.createPatternFromText(pattern.name, pattern.getManager)
    pattern.replace(newPattern)
  }

  def addToParameter(param: ScParameter) {
    param.parentsInFile.findByType(classOf[ScFunctionExpr]) match {
      case Some(func) =>
        val index = func.parameters.indexOf(param)
        func.expectedType() match {
          case Some(ScFunctionType(_, params)) =>
            if (index >= 0 && index < params.length) {
              val paramExpectedType = params(index)
              val param1 = param.getParent match {
                case x: ScParameterClause if x.parameters.length == 1 =>
                  // ensure  that the parameter is wrapped in parentheses before we add the type annotation.
                  val clause: PsiElement = x.replace(ScalaPsiElementFactory.createClauseForFunctionExprFromText("(" + param.getText + ")", param.getManager))
                  clause.asInstanceOf[ScParameterClause].parameters.head
                case _ => param
              }
              addTypeAnnotation(paramExpectedType, param1.getParent, param1)
            }
          case _ =>
        }
      case _ =>
    }
  }

  def removeFromParameter(param: ScParameter) {
    val newParam = ScalaPsiElementFactory.createParameterFromText(param.name, param.getManager)
    val newClause = ScalaPsiElementFactory.createClauseForFunctionExprFromText(newParam.getText, param.getManager)
    val expr : ScFunctionExpr = PsiTreeUtil.getParentOfType(param, classOf[ScFunctionExpr], false)
    if (expr != null && expr.parameters.size == 1 &&
            (expr.params.clauses(0).getText.startsWith("(") && expr.params.clauses(0).getText.endsWith(")"))) {
      expr.params.clauses(0).replace(newClause)
    } else {
      param.replace(newParam)
    }
  }

  def addTypeAnnotation(t: ScType, context: PsiElement, anchor: PsiElement) {
    val annotation = ScalaPsiElementFactory.createTypeElementFromText(t.canonicalText, context.getManager)
    val added = context.addAfter(annotation, anchor)

    val colon = ScalaPsiElementFactory.createColon(context.getManager)
    context.addAfter(colon, anchor)

    ScalaPsiUtil.adjustTypes(added)
  }

  def removeTypeAnnotation(e: PsiElement) {
    e.prevSiblings.find(_.getText == ":").foreach(_.delete())
    e.delete()
  }
}