package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.{ScTypeText, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 22.12.15.
  */
class MakeTypeMoreSpecificIntention extends PsiElementBaseIntentionAction {
  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    implicit val typeSystem = project.typeSystem
    ToggleTypeAnnotation.complete(new MakeTypeMoreSpecificStrategy(Option(editor)), element)
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (element == null || !IntentionAvailabilityChecker.checkIntention(this, element)) false
    else {
      var isAvailable = false
      def text(s: String): Unit = {
        setText(s)
        isAvailable = true
      }
      implicit val typeSystem = project.typeSystem
      val desc = new StrategyAdapter {
        override def removeFromVariable(variable: ScVariableDefinition): Unit = {
          for {
            declared <- variable.declaredType
            expr <- variable.expr
            tp <- expr.getType()
            if MakeTypeMoreSpecificStrategy.computeBaseTypes(declared, tp).nonEmpty
          } text(ScalaBundle.message("make.type.more.specific"))
        }

        override def removeFromValue(value: ScPatternDefinition): Unit = {
          for {
            declared <- value.declaredType
            expr <- value.expr
            tp <- expr.getType()
            if MakeTypeMoreSpecificStrategy.computeBaseTypes(declared, tp).nonEmpty
          } text(ScalaBundle.message("make.type.more.specific"))
        }

        override def removeFromFunction(function: ScFunctionDefinition): Unit = {
          for {
            declared <- function.returnType
            expr <- function.body
            tp <- expr.getType()
            if MakeTypeMoreSpecificStrategy.computeBaseTypes(declared, tp).nonEmpty
          } text(ScalaBundle.message("make.type.more.specific.fun"))
        }
      }

      ToggleTypeAnnotation.complete(desc, element)
      isAvailable
    }
  }

  override def getFamilyName: String = ScalaBundle.message("make.type.more.specific")
}

class MakeTypeMoreSpecificStrategy(editor: Option[Editor])
                                  (implicit typeSystem: TypeSystem) extends Strategy {
  import MakeTypeMoreSpecificStrategy._

  def doTemplate(te: ScTypeElement, declaredType: ScType, dynamicType: ScType, context: PsiElement, editor: Editor): Unit = {
    val types = computeBaseTypes(declaredType, dynamicType).sortWith((t1, t2) => t1.conforms(t2))
    if (types.size == 1) {
      val replaced = te.replace(ScalaPsiElementFactory.createTypeElementFromText(types.head.canonicalText, te.getContext, te))
      TypeAdjuster.markToAdjust(replaced)
    } else {
      val texts = types.map(ScTypeText)
      val expr = new ChooseTypeTextExpression(texts, ScTypeText(declaredType))
      IntentionUtil.startTemplate(te, context, expr, editor)
    }
  }


  override def removeFromFunction(function: ScFunctionDefinition): Unit = {
    for {
      edit <- editor
      te <- function.returnTypeElement
      body <- function.body
      tp <- body.getType()
      declared <- te.getType()
    } doTemplate(te, declared, tp, function.getParent, edit)
  }

  override def removeFromValue(value: ScPatternDefinition): Unit = {
    for {
      edit <- editor
      te <- value.typeElement
      body <- value.expr
      tp <- body.getType()
      declared <- te.getType()
    } doTemplate(te, declared, tp, value.getParent, edit)
  }

  override def removeFromVariable(variable: ScVariableDefinition): Unit = {
    for {
      edit <- editor
      te <- variable.typeElement
      body <- variable.expr
      tp <- body.getType()
      declared <- te.getType()
    } doTemplate(te, declared, tp, variable.getParent, edit)
  }

  override def addToPattern(pattern: ScBindingPattern): Unit = ()

  override def addToWildcardPattern(pattern: ScWildcardPattern): Unit = ()

  override def addToValue(value: ScPatternDefinition): Unit = ()

  override def addToFunction(function: ScFunctionDefinition): Unit = ()

  override def removeFromPattern(pattern: ScTypedPattern): Unit = ()

  override def addToVariable(variable: ScVariableDefinition): Unit = ()

  override def removeFromParameter(param: ScParameter): Unit = ()

  override def addToParameter(param: ScParameter): Unit = ()
}

object MakeTypeMoreSpecificStrategy {
  def computeBaseTypes(declaredType: ScType, dynamicType: ScType)
                      (implicit typeSystem: TypeSystem) : Seq[ScType] = {
    val baseTypes = dynamicType +: BaseTypes.get(dynamicType)
    baseTypes.filter(t => t.conforms(declaredType) && !t.equiv(declaredType))
  }
}