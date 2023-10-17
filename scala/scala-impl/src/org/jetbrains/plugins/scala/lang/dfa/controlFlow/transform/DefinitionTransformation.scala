package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import com.intellij.codeInspection.dataFlow.lang.ir.SimpleAssignmentInstruction
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScBlockStatement, ScExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScFunctionDefinition, ScPatternDefinition, ScValueOrVariableDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait DefinitionTransformation  { this: ScalaDfaControlFlowBuilder =>
  def transformDefinition(element: ScDefinitionWithAssignment): Unit = element match {
    case patternDefinition: ScPatternDefinition => transformPatternDefinition(patternDefinition)
    case variableDefinition: ScVariableDefinition => transformVariableDefinition(variableDefinition)
    case _: ScFunctionDefinition => // nothing to do
    //case otherStatementDefinition: ScBlockStatement => pushUnknownCall(otherStatementDefinition, 0)
    case _ => throw TransformationFailedException(element, "Unsupported definition.")
  }

  private def transformPatternDefinition(definition: ScPatternDefinition): Unit =
    transformDefinitionIfSimple(definition, definition.isStable)

  private def transformVariableDefinition(definition: ScVariableDefinition): Unit =
    transformDefinitionIfSimple(definition, isStable = false)

  private def transformDefinitionIfSimple(definition: ScValueOrVariableDefinition, isStable: Boolean): Unit = {
    if (!definition.isSimple) {
      buildUnknownCall(definition, 0, ResultReq.None)
    } else {
      val binding = definition.bindings.head
      val descriptor = ScalaDfaVariableDescriptor(binding, None, isStable && binding.isStable)
      val definedType = definition.`type`().getOrAny

      if (definition.expr.exists(canBeClassInstantiationExpression)) {
        assignVariableValueWithInstanceQualifier(descriptor, definition.expr, binding, definedType)
      } else {
        assignVariableValue(descriptor, definition.expr, definedType)
      }
    }
  }

  private def canBeClassInstantiationExpression(expression: ScExpression): Boolean = {
    expression.is[ScNewTemplateDefinition, MethodInvocation]
  }

  private def assignVariableValueWithInstanceQualifier(descriptor: ScalaDfaVariableDescriptor,
                                                       instantiationExpression: Option[ScExpression],
                                                       instanceQualifier: ScBindingPattern, definedType: ScType): Unit = {
    val dfaVariable = createVariable(descriptor)
    val anchor = instantiationExpression.map(ScalaStatementAnchor(_)).orNull
    val qualifierVariable = ScalaDfaVariableDescriptor(instanceQualifier, None, instanceQualifier.isStable)

    instantiationExpression match {
      case Some(expression) =>
        transformInvocation(expression, ResultReq.Required, Some(qualifierVariable))
        buildImplicitConversion(Some(expression), Some(definedType))
      case _ =>
        pushUnknownValue()
    }

    addInstruction(new SimpleAssignmentInstruction(anchor, dfaVariable))
  }
}
