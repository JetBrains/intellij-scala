package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ModifiableEvaluator, ModifiableValue, Modifier}
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.project.Project
import com.sun.jdi._
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaFieldEvaluator.MyModifier
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

/**
 * Follows the implementation details of `com.intellij.debugger.engine.evaluation.expression.FieldEvaluator` but is
 * adapted for Scala.
 */
case class ScalaFieldEvaluator(objectEvaluator: Evaluator, _fieldName: String,
                          classPrivateThisField: Boolean = false) extends ModifiableEvaluator {

  private val fieldName = DebuggerUtil.withoutBackticks(_fieldName)

  private def fieldByName(t: ReferenceType, fieldName: String): Field = {
    if (classPrivateThisField) {
      t.fields().forEach( field =>
        if (field.name().endsWith("$$" + fieldName))
          return field
      )
    }
    var field = t.fieldByName(fieldName)
    if (field != null) {
      return field
    }
    for (i <- 1 to 3) {
      field = t.fieldByName(fieldName + "$" + i)
      if (field != null) return field
    }
    t.fields().forEach( field =>
      if (field.name().startsWith(fieldName + "$"))
        return field
    )
    null
  }
  
  private def findField(t: Type, context: EvaluationContextImpl): Field = {
    t match {
      case cls: ClassType =>
        val foundInClass = fieldByName(cls, fieldName)
        if (foundInClass != null) return foundInClass

        cls.interfaces.forEach { interfaceType =>
          val field: Field = findField(interfaceType, context)
          if (field != null) {
            return field
          }
        }
        return findField(cls.superclass, context)
      case iface: InterfaceType =>
        val foundInInterface = fieldByName(iface, fieldName)
        if (foundInInterface != null) return foundInInterface

        iface.superinterfaces.forEach { interfaceType =>
          val field: Field = findField(interfaceType, context)
          if (field != null) {
            return field
          }
        }
      case _ =>
    }
    null
  }

  @NotNull
  override def evaluateModifiable(context: EvaluationContextImpl): ModifiableValue = {
    val obj: AnyRef = DebuggerUtil.unwrapScalaRuntimeRef {
      objectEvaluator.evaluate(context)
    }
    evaluateField(obj, context)
  }

  @NotNull
  private def evaluateField(obj: AnyRef, context: EvaluationContextImpl): ModifiableValue = {
    obj match {
      case refType: ReferenceType =>
        var field: Field = findField(refType, context)
        if (field == null || !field.isStatic) {
          field = fieldByName(refType, fieldName)
        }
        if (field == null || !field.isStatic) {
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.no.static.field", fieldName))
        }
        val modifier = new MyModifier(refType, field)
        new ModifiableValue(refType.getValue(field), modifier)
      case objRef: ObjectReference =>
        val refType: ReferenceType = objRef.referenceType
        if (!(refType.isInstanceOf[ClassType] || refType.isInstanceOf[ArrayType])) {
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.class.or.array.expected", fieldName))
        }
        objRef match {
          case arrayRef: ArrayReference if "length" == fieldName =>
            val value = context.getVirtualMachineProxy.mirrorOf(arrayRef.length())
            return new ModifiableValue(value, null)
          case _ =>
        }
        var field: Field = findField(refType, context)
        if (field == null) {
          field = refType.fieldByName(fieldName)
        }
        if (field == null) {
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.no.instance.field", fieldName))
        }
        val qualifier = if (field.isStatic) refType else objRef
        val modifier = new MyModifier(qualifier, field)
        val value = if (field.isStatic) refType.getValue(field) else objRef.getValue(field)
        new ModifiableValue(value, modifier)
      case null => throw EvaluationException(new NullPointerException)
      case _ =>
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.field", fieldName))
    }
  }
}

object ScalaFieldEvaluator {
  //noinspection InstanceOf
  private final class MyModifier(evaluatedQualifier: AnyRef, evaluatedField: Field) extends Modifier {
    override def canInspect: Boolean = evaluatedQualifier.isInstanceOf[ObjectReference]
    override def canSetValue: Boolean = true
    override def setValue(value: Value): Unit = {
      if (evaluatedQualifier.isInstanceOf[ReferenceType]) {
        val classType = evaluatedQualifier.asInstanceOf[ClassType]
        classType.setValue(evaluatedField, value)
      } else {
        val objRef = evaluatedQualifier.asInstanceOf[ObjectReference]
        objRef.setValue(evaluatedField, value)
      }
    }
    override def getExpectedType: Type = evaluatedField.`type`()
    override def getInspectItem(project: Project): NodeDescriptor = {
      evaluatedQualifier match {
        case objRef: ObjectReference =>
          new FieldDescriptorImpl(project, objRef, evaluatedField)
        case _ => null
      }
    }
  }
}
