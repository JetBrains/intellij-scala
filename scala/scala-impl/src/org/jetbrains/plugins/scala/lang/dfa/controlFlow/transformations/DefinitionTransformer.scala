package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.lang.ir.SimpleAssignmentInstruction
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor}
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
    transformSimpleDefinition(definition, builder, definition.isStable)
  }

  private def transformVariableDefinition(definition: ScVariableDefinition, builder: ScalaDfaControlFlowBuilder): Unit = {
    transformSimpleDefinition(definition, builder, isStable = false)
  }

  private def transformSimpleDefinition(definition: ScValueOrVariableDefinition, builder: ScalaDfaControlFlowBuilder,
                                        isStable: Boolean): Unit = {
    if (!definition.isSimple) {
      builder.pushUnknownValue()
      return
    }

    val binding = definition.bindings.head
    val dfaVariable = builder.createVariable(ScalaDfaVariableDescriptor(binding, isStable && binding.isStable))

    transformIfPresent(definition.expr, builder)
    builder.pushInstruction(new SimpleAssignmentInstruction(definition.expr.map(ScalaStatementAnchor(_)).orNull, dfaVariable))
  }
}
