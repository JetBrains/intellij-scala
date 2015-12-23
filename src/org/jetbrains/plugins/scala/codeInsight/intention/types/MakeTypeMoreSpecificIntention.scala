package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScType, ScTypeText}
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 22.12.15.
  */
class MakeTypeMoreSpecificIntention extends PsiElementBaseIntentionAction {
  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    ToggleTypeAnnotation.complete(MakeTypeMoreSpecificStrategy, element, Option(editor))
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (element == null || !IntentionAvailabilityChecker.checkIntention(this, element)) false
    else {
      var isAvailable = false
      def text(s: String): Unit = {
        setText(s)
        isAvailable = true
      }
      val desc = new StrategyAdapter {
        override def removeFromVariable(variable: ScVariableDefinition, editor: Option[Editor]): Unit = {
          for {
            declared <- variable.declaredType
            expr <- variable.expr
            tp <- expr.getType()
            if MakeTypeMoreSpecificStrategy.computeBaseTypes(declared, tp).toSet.size > 1
          } text(ScalaBundle.message("make.type.more.specific"))
        }

        override def removeFromValue(value: ScPatternDefinition, editor: Option[Editor]): Unit = {
          for {
            declared <- value.declaredType
            expr <- value.expr
            tp <- expr.getType()
            if MakeTypeMoreSpecificStrategy.computeBaseTypes(declared, tp).toSet.size > 1
          } text(ScalaBundle.message("make.type.more.specific"))
        }

        override def removeFromFunction(function: ScFunctionDefinition, editor: Option[Editor]): Unit = {
          for {
            declared <- function.returnType
            expr <- function.body
            tp <- expr.getType()
            if MakeTypeMoreSpecificStrategy.computeBaseTypes(declared, tp).toSet.size > 1
          } text(ScalaBundle.message("make.type.more.specific.fun"))
        }
      }

      ToggleTypeAnnotation.complete(desc, element, Option(editor))
      isAvailable
    }
  }

  override def getFamilyName: String = ScalaBundle.message("make.type.more.specific")
}

object MakeTypeMoreSpecificStrategy extends Strategy {
  def computeBaseTypes(declaredType: ScType, dynamicType: ScType): Seq[ScType] = {
    val baseTypes = dynamicType +: BaseTypes.get(dynamicType) :+ declaredType
    baseTypes.filter(_.conforms(declaredType))
  }

  def doTemplate(te: ScTypeElement, declaredType: ScType, dynamicType: ScType, context: PsiElement, editor: Editor): Unit = {
    val types = computeBaseTypes(declaredType, dynamicType).sortWith((t1, t2) => t1.conforms(t2))
    val texts = types.map(ScTypeText)
    val expr = new ChooseTypeTextExpression(texts, ScTypeText(declaredType))
    IntentionUtil.startTemplate(te, context, expr, editor)
  }


  override def removeFromFunction(function: ScFunctionDefinition, editor: Option[Editor]): Unit = {
    for {
      edit <- editor
      te <- function.returnTypeElement
      body <- function.body
      tp <- body.getType()
      declared <- te.getType()
    } doTemplate(te, declared, tp, function.getParent, edit)
  }

  override def removeFromValue(value: ScPatternDefinition, editor: Option[Editor]): Unit = {
    for {
      edit <- editor
      te <- value.typeElement
      body <- value.expr
      tp <- body.getType()
      declared <- te.getType()
    } doTemplate(te, declared, tp, value.getParent, edit)
  }

  override def removeFromVariable(variable: ScVariableDefinition, editor: Option[Editor]): Unit = {
    for {
      edit <- editor
      te <- variable.typeElement
      body <- variable.expr
      tp <- body.getType()
      declared <- te.getType()
    } doTemplate(te, declared, tp, variable.getParent, edit)
  }

  override def addToPattern(pattern: ScBindingPattern, editor: Option[Editor]): Unit = ()

  override def addToWildcardPattern(pattern: ScWildcardPattern, editor: Option[Editor]): Unit = ()

  override def addToValue(value: ScPatternDefinition, editor: Option[Editor]): Unit = ()

  override def addToFunction(function: ScFunctionDefinition, editor: Option[Editor]): Unit = ()

  override def removeFromPattern(pattern: ScTypedPattern, editor: Option[Editor]): Unit = ()

  override def addToVariable(variable: ScVariableDefinition, editor: Option[Editor]): Unit = ()

  override def removeFromParameter(param: ScParameter, editor: Option[Editor]): Unit = ()

  override def addToParameter(param: ScParameter, editor: Option[Editor]): Unit = ()
}