package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypeText
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScType, ScTypeExt}

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 22.12.15.
  */
class MakeTypeMoreSpecificIntention extends AbstractTypeAnnotationIntention {

  import MakeTypeMoreSpecificIntention._

  override def getFamilyName: String = FamilyName

  override protected def descriptionStrategy: Strategy = new Strategy {

    override def variableWithType(variable: ScVariableDefinition,
                                  typeElement: ScTypeElement): Unit = {
      for {
        declared <- variable.declaredType
        expr <- variable.expr
        tp <- expr.getType()
        if computeBaseTypes(declared, tp).nonEmpty
      } setText(message("make.type.more.specific"))
    }

    override def valueWithType(value: ScPatternDefinition,
                               typeElement: ScTypeElement): Unit = {
      for {
        declared <- value.declaredType
        expr <- value.expr
        tp <- expr.getType()
        if computeBaseTypes(declared, tp).nonEmpty
      } setText(message("make.type.more.specific"))
    }

    override def functionWithType(function: ScFunctionDefinition,
                                  typeElement: ScTypeElement): Unit = {
      for {
        declared <- function.returnType
        expr <- function.body
        tp <- expr.getType()
        if computeBaseTypes(declared, tp).nonEmpty
      } setText(message("make.type.more.specific.fun"))
    }
  }

  override protected def invocationStrategy(maybeEditor: Option[Editor]): Strategy = new Strategy {

    private def doTemplate(te: ScTypeElement, declaredType: ScType, dynamicType: ScType, context: PsiElement): Unit = {
      val types = computeBaseTypes(declaredType, dynamicType).sortWith((t1, t2) => t1.conforms(t2))
      if (types.size == 1) {
        val replaced = te.replace(ScalaPsiElementFactory.createTypeElementFromText(types.head.canonicalText, te.getContext, te))
        TypeAdjuster.markToAdjust(replaced)
      } else {
        val texts = types.map(ScTypeText)
        val expr = new ChooseTypeTextExpression(texts, ScTypeText(declaredType))
        IntentionUtil.startTemplate(te, context.getParent, expr, maybeEditor.get)
      }
    }

    override def functionWithType(function: ScFunctionDefinition,
                                  typeElement: ScTypeElement): Unit = {
      for {
        body <- function.body
        if maybeEditor.isDefined
        tp <- body.getType()
        declared <- typeElement.getType()
      } doTemplate(typeElement, declared, tp, function)
    }

    override def valueWithType(value: ScPatternDefinition,
                               typeElement: ScTypeElement): Unit = {
      for {
        body <- value.expr
        if maybeEditor.isDefined
        tp <- body.getType()
        declared <- typeElement.getType()
      } doTemplate(typeElement, declared, tp, value)
    }

    override def variableWithType(variable: ScVariableDefinition,
                                  typeElement: ScTypeElement): Unit = {
      for {
        body <- variable.expr
        if maybeEditor.isDefined
        tp <- body.getType()
        declared <- typeElement.getType()
      } doTemplate(typeElement, declared, tp, variable)
    }
  }
}

object MakeTypeMoreSpecificIntention {

  private[types] val FamilyName: String =
    message("make.type.more.specific")

  private def computeBaseTypes(declaredType: ScType, dynamicType: ScType): Seq[ScType] = {
    val baseTypes = dynamicType +: BaseTypes.get(dynamicType)
    baseTypes.filter(_.conforms(declaredType))
      .filter(!_.equiv(declaredType))
  }
}