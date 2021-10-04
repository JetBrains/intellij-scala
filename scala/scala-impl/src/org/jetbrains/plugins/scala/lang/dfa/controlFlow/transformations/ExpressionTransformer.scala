package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.java.inst.JvmPushInstruction
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.types.DfTypes
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.dfa.framework.{ScalaStatementAnchor, ScalaUnreportedElementAnchor}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.literalToDfType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class ExpressionTransformer(val expression: ScExpression) extends ScalaPsiElementTransformer(expression) {

  override def toString: String = s"ExpressionTransformer: $expression"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = expression match {
    case block: ScBlockExpr => transformBlock(block, builder)
    case parenthesised: ScParenthesisedExpr => transformParenthesisedExpression(parenthesised, builder)
    case literal: ScLiteral => transformLiteral(literal, builder)
    case _: ScUnitExpr => transformUnitExpression(builder)
    case ifExpression: ScIf => transformIfExpression(ifExpression, builder)
    case reference: ScReferenceExpression => transformReferenceExpression(reference, builder)
    case methodCall: ScMethodCall => transformMethodCall(methodCall, builder)
    case templateDefinition: ScTemplateDefinition => transformTemplateDefinition(templateDefinition, builder)
    case _ => throw TransformationFailedException(expression, "Unsupported expression.")
  }

  private def transformBlock(block: ScBlockExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
    val statements = block.statements
    if (statements.isEmpty) {
      builder.pushUnknownValue()
    } else {
      statements.init.foreach { statement =>
        new ScalaPsiElementTransformer(statement).transform(builder)
        builder.popReturnValue()
      }

      transformPsiElement(statements.last, builder)
      builder.pushInstruction(new FinishElementInstruction(block))
    }
  }

  private def transformParenthesisedExpression(parenthesised: ScParenthesisedExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
    parenthesised.innerElement.foreach(transformPsiElement(_, builder))
  }

  private def transformLiteral(literal: ScLiteral, builder: ScalaDfaControlFlowBuilder): Unit = {
    builder.pushInstruction(new PushValueInstruction(literalToDfType(literal), ScalaStatementAnchor(literal)))
  }

  private def transformUnitExpression(builder: ScalaDfaControlFlowBuilder): Unit = builder.pushUnknownValue()

  private def transformIfExpression(ifExpression: ScIf, builder: ScalaDfaControlFlowBuilder): Unit = {
    for (condition <- ifExpression.condition) {
      val skipThenOffset = new DeferredOffset
      val skipElseOffset = new DeferredOffset

      transformPsiElement(condition, builder)
      builder.pushInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, condition))

      builder.pushInstruction(new FinishElementInstruction(null))
      transformIfPresent(ifExpression.thenExpression, builder)
      builder.pushInstruction(new GotoInstruction(skipElseOffset))
      builder.setOffset(skipThenOffset)

      builder.pushInstruction(new FinishElementInstruction(null))
      transformIfPresent(ifExpression.elseExpression, builder)
      builder.setOffset(skipElseOffset)

      builder.pushInstruction(new FinishElementInstruction(ifExpression))
    }
  }

  private def transformReferenceExpression(expression: ScReferenceExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO check somehow if this reference expression is an invocation or not, if it is, handle it with InvocationTransformer

    // TODO add qualified expressions, currently only simple ones
    expression.getReference.bind().map(_.element) match {
      case Some(element) => // TODO extract later + try to fix types/anchor, if possible
        val dfaVariable = builder.createVariable(ScalaVariableDescriptor(element, isStable = true))
        builder.pushInstruction(new JvmPushInstruction(dfaVariable, ScalaUnreportedElementAnchor(element)))
      case _ => builder.pushUnknownCall(expression, 0)
    }
  }

  private def transformMethodCall(methodCall: ScMethodCall, builder: ScalaDfaControlFlowBuilder): Unit = {
    new InvocationTransformer(methodCall).transform(builder)
  }

  private def transformTemplateDefinition(templateDefinition: ScTemplateDefinition, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement
    builder.pushUnknownValue()
  }
}
