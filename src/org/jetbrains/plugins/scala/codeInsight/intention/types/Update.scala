package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions._
import lang.psi.ScalaPsiUtil
import lang.psi.api.statements.params.ScParameter
import lang.psi.api.expr.ScFunctionExpr

/**
 * Pavel.Fatin, 28.04.2010
 */

object Update extends Strategy {
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

  def removeFromPattern(pattern: ScTypedPattern) {
    pattern.typePattern.foreach(removeTypeAnnotation)
  }

  def addToParameter(param: ScParameter) {
    param.parentsInFile.findByType(classOf[ScFunctionExpr]) match {
      case Some(func) =>
        val index = func.parameters.indexOf(param)
        func.expectedType.flatMap(ScType.extractFunctionType) match {
          case Some(funcType) =>
            if (index >= 0 && index < funcType.arity) {
              val paramExpectedType = funcType.params(index)
              addTypeAnnotation(paramExpectedType, param.getParent, param)
            }
          case None =>
        }
      case _ =>
    }
  }

  def removeFromParameter(param: ScParameter) {
    val newParam = ScalaPsiElementFactory.createParameterFromText(param.getName, param.getManager)
    param.replace(newParam)
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