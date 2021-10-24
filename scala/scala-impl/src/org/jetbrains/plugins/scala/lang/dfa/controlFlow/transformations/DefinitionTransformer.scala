package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScBlockStatement, ScExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScFunctionDefinition, ScPatternDefinition, ScValueOrVariableDefinition, ScVariableDefinition}

class DefinitionTransformer(val wrappedDefinition: ScDefinitionWithAssignment)
  extends ScalaPsiElementTransformer(wrappedDefinition) {

  override def toString: String = s"DefinitionTransformer: $wrappedDefinition"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = wrappedDefinition match {
    case patternDefinition: ScPatternDefinition => transformPatternDefinition(patternDefinition, builder)
    case variableDefinition: ScVariableDefinition => transformVariableDefinition(variableDefinition, builder)
    case _: ScFunctionDefinition => builder.pushUnknownValue()
    case otherStatementDefinition: ScBlockStatement => builder.pushUnknownCall(otherStatementDefinition, 0)
    case _ => throw TransformationFailedException(wrappedDefinition, "Unsupported definition.")
  }

  private def transformPatternDefinition(definition: ScPatternDefinition, builder: ScalaDfaControlFlowBuilder): Unit = {
    transformDefinitionIfSimple(definition, builder, definition.isStable)
  }

  private def transformVariableDefinition(definition: ScVariableDefinition, builder: ScalaDfaControlFlowBuilder): Unit = {
    transformDefinitionIfSimple(definition, builder, isStable = false)
  }

  private def transformDefinitionIfSimple(definition: ScValueOrVariableDefinition, builder: ScalaDfaControlFlowBuilder,
                                          isStable: Boolean): Unit = {
    if (!definition.isSimple) {
      builder.pushUnknownCall(definition, 0)
    } else {
      val binding = definition.bindings.head
      val descriptor = ScalaDfaVariableDescriptor(binding, None, isStable && binding.isStable)

      if (definition.expr.exists(canBeClassInstantiationExpression)) {
        builder.assignVariableValueWithInstanceQualifier(descriptor, definition.expr, binding)
      } else {
        builder.assignVariableValue(descriptor, definition.expr)
      }
    }
  }

  private def canBeClassInstantiationExpression(expression: ScExpression): Boolean = {
    expression.is[ScNewTemplateDefinition, MethodInvocation]
  }
}
