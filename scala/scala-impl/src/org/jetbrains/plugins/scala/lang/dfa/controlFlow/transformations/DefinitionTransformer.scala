package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.lang.ir.SimpleAssignmentInstruction
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.dfa.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScPatternDefinition}

class DefinitionTransformer(definition: ScDefinitionWithAssignment) extends ScalaPsiElementTransformer(definition) {

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = definition match {
    case patternDefinition: ScPatternDefinition => transformPatternDefinition(patternDefinition, builder)
    case _ => throw TransformationFailedException(definition, "Unsupported definition.")
  }

  private def transformPatternDefinition(definition: ScPatternDefinition, builder: ScalaDfaControlFlowBuilder): Unit = {
    if (!definition.isSimple) {
      builder.pushUnknownValue()
      return
    }

    val binding = definition.bindings.head
    val dfaVariable = builder.createVariable(ScalaVariableDescriptor(binding, binding.isStable))

    transformIfPresent(definition.expr, builder)
    builder.pushInstruction(new SimpleAssignmentInstruction(ScalaStatementAnchor(definition), dfaVariable))
  }
}
