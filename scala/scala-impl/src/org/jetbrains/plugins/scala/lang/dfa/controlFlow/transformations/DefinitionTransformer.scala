package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScPatternDefinition, ScValueOrVariableDefinition, ScVariableDefinition}

class DefinitionTransformer(val wrappedDefinition: ScDefinitionWithAssignment)
  extends ScalaPsiElementTransformer(wrappedDefinition) {

  override def toString: String = s"DefinitionTransformer: $wrappedDefinition"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = wrappedDefinition match {
    case patternDefinition: ScPatternDefinition => transformPatternDefinition(patternDefinition, builder)
    case variableDefinition: ScVariableDefinition => transformVariableDefinition(variableDefinition, builder)
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
      val descriptor = ScalaDfaVariableDescriptor(binding, isStable && binding.isStable)
      builder.assignVariableValue(descriptor, definition.expr)
    }
  }
}
