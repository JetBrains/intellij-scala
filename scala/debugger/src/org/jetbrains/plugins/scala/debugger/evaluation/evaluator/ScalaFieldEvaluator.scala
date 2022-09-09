package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.ui.impl.watch.{FieldDescriptorImpl, NodeDescriptorImpl}
import com.intellij.openapi.project.Project
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

case class ScalaFieldEvaluator(objectEvaluator: Evaluator, _fieldName: String,
                          classPrivateThisField: Boolean = false) extends Evaluator {
  private var myEvaluatedQualifier: AnyRef = _
  private var myEvaluatedField: Field = _

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

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    myEvaluatedField = null
    myEvaluatedQualifier = null
    val obj: AnyRef = DebuggerUtil.unwrapScalaRuntimeRef {
      objectEvaluator.evaluate(context)
    }
    evaluateField(obj, context)
  }

  private def evaluateField(obj: AnyRef, context: EvaluationContextImpl): AnyRef = {
    obj match {
      case refType: ReferenceType =>
        var field: Field = findField(refType, context)
        if (field == null || !field.isStatic) {
          field = fieldByName(refType, fieldName)
        }
        if (field == null || !field.isStatic) {
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.no.static.field", fieldName))
        }
        myEvaluatedField = field
        myEvaluatedQualifier = refType
        refType.getValue(field)
      case objRef: ObjectReference =>
        val refType: ReferenceType = objRef.referenceType
        if (!(refType.isInstanceOf[ClassType] || refType.isInstanceOf[ArrayType])) {
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.class.or.array.expected", fieldName))
        }
        objRef match {
          case arrayRef: ArrayReference if "length" == fieldName =>
            return DebuggerUtilsEx.createValue(context.getDebugProcess.getVirtualMachineProxy, "int", arrayRef.length)
          case _ =>
        }
        var field: Field = findField(refType, context)
        if (field == null) {
          field = refType.fieldByName(fieldName)
        }
        if (field == null) {
          throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.no.instance.field", fieldName))
        }
        myEvaluatedQualifier = if (field.isStatic) refType else objRef
        myEvaluatedField = field
        if (field.isStatic) refType.getValue(field) else objRef.getValue(field)
      case null => throw EvaluationException(new NullPointerException)
      case _ =>
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.evaluating.field", fieldName))
    }
  }

  override def getModifier: Modifier = {
    var modifier: Modifier = null
    if (myEvaluatedField != null &&
      (myEvaluatedQualifier.isInstanceOf[ClassType] ||
        myEvaluatedQualifier.isInstanceOf[ObjectReference])) {
      modifier = new Modifier {
        override def canInspect: Boolean = {
          myEvaluatedQualifier.isInstanceOf[ObjectReference]
        }

        override def canSetValue: Boolean = {
          true
        }

        override def setValue(value: Value): Unit = {
          if (myEvaluatedQualifier.isInstanceOf[ReferenceType]) {
            val classType: ClassType = myEvaluatedQualifier.asInstanceOf[ClassType]
            classType.setValue(myEvaluatedField, value)
          }
          else {
            val objRef: ObjectReference = myEvaluatedQualifier.asInstanceOf[ObjectReference]
            objRef.setValue(myEvaluatedField, value)
          }
        }

        override def getExpectedType: Type = {
          myEvaluatedField.`type`
        }

        override def getInspectItem(project: Project): NodeDescriptorImpl = {
          myEvaluatedQualifier match {
            case reference: ObjectReference =>
              new FieldDescriptorImpl(project, reference, myEvaluatedField)
            case _ => null
          }
        }
      }
    }
    modifier
  }
}