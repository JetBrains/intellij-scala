package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.ui.impl.watch.{FieldDescriptorImpl, NodeDescriptorImpl}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiAnonymousClass, PsiClass, PsiElement}
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * User: Alefas
 * Date: 12.10.11
 */
object ScalaFieldEvaluator {
  def getFilter(clazz: PsiElement): ReferenceType => Boolean = {
    clazz match {
      case a: ScNewTemplateDefinition => ref => true
      case a: PsiAnonymousClass => ref => true
      case e: ScExpression => ref => true
      case td: ScTypeDefinition =>
        if (ScalaPsiUtil.isLocalClass(td)) {
          val fqn = td.getQualifiedNameForDebugger
          ref => {
            val refName = ref.name()
            val index = refName.lastIndexOf(fqn)
            if (index < 0) false
            else {
              val suffix = refName.substring(index + fqn.length())
              suffix.find(c => c != '$' && !c.isDigit) == None
            }
          }
        } else {
          val fqn = td.getQualifiedNameForDebugger
          ref => ref.name() == fqn
        }
      case p: PsiClass =>
        val name = JVMNameUtil.getNonAnonymousClassName(p)
        if (name == null) ref => true
        else ref => ref.name() == name
      case _ => ref => true
    }
  }
}

case class ScalaFieldEvaluator(objectEvaluator: Evaluator, filter: ReferenceType => Boolean, _fieldName: String,
                          classPrivateThisField: Boolean = false) extends Evaluator {
  private var myEvaluatedQualifier: AnyRef = null
  private var myEvaluatedField: Field = null

  private val fieldName = DebuggerUtil.withoutBackticks(_fieldName)

  private def fieldByName(t: ReferenceType, fieldName: String): Field = {
    if (classPrivateThisField) {
      import scala.collection.JavaConversions._
      for (field <- t.fields()) {
        if (field.name().endsWith("$$" + fieldName)) return field
      }
    }
    var field = t.fieldByName(fieldName)
    if (field != null) {
      return field
    }
    for (i <- 1 to 3) {
      field = t.fieldByName(fieldName + "$" + i)
      if (field != null) return field
    }
    import scala.collection.JavaConversions._
    for (field <- t.fields()) {
      if (field.name().startsWith(fieldName + "$")) return field
    }
    null
  }
  
  private def findField(t: Type, context: EvaluationContextImpl): Field = {
    t match {
      case cls: ClassType =>
        if (filter(cls)) {
          return fieldByName(cls, fieldName)
        }
        import scala.collection.JavaConversions._
        for (interfaceType <- cls.interfaces) {
          val field: Field = findField(interfaceType, context)
          if (field != null) {
            return field
          }
        }
        return findField(cls.superclass, context)
      case iface: InterfaceType =>
        if (filter(iface)) {
          return fieldByName(iface, fieldName)
        }
        import scala.collection.JavaConversions._
        for (interfaceType <- iface.superinterfaces) {
          val field: Field = findField(interfaceType, context)
          if (field != null) {
            return field
          }
        }
      case _ =>
    }
    null
  }

  def evaluate(context: EvaluationContextImpl): AnyRef = {
    myEvaluatedField = null
    myEvaluatedQualifier = null
    val obj: AnyRef = DebuggerUtil.unwrapScalaRuntimeObjectRef {
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
          throw EvaluationException(DebuggerBundle.message("evaluation.error.no.static.field", fieldName))
        }
        myEvaluatedField = field
        myEvaluatedQualifier = refType
        refType.getValue(field)
      case objRef: ObjectReference =>
        val refType: ReferenceType = objRef.referenceType
        if (!(refType.isInstanceOf[ClassType] || refType.isInstanceOf[ArrayType])) {
          throw EvaluationException(DebuggerBundle.message("evaluation.error.class.or.array.expected", fieldName))
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
          throw EvaluationException(DebuggerBundle.message("evaluation.error.no.instance.field", fieldName))
        }
        myEvaluatedQualifier = if (field.isStatic) refType else objRef
        myEvaluatedField = field
        if (field.isStatic) refType.getValue(field) else objRef.getValue(field)
      case null => throw EvaluationException(new NullPointerException)
      case _ =>
        throw EvaluationException(DebuggerBundle.message("evaluation.error.evaluating.field", fieldName))
    }
  }

  def getModifier: Modifier = {
    var modifier: Modifier = null
    if (myEvaluatedField != null &&
      (myEvaluatedQualifier.isInstanceOf[ClassType] ||
        myEvaluatedQualifier.isInstanceOf[ObjectReference])) {
      modifier = new Modifier {
        def canInspect: Boolean = {
          myEvaluatedQualifier.isInstanceOf[ObjectReference]
        }

        def canSetValue: Boolean = {
          true
        }

        def setValue(value: Value) {
          if (myEvaluatedQualifier.isInstanceOf[ReferenceType]) {
            val classType: ClassType = myEvaluatedQualifier.asInstanceOf[ClassType]
            classType.setValue(myEvaluatedField, value)
          }
          else {
            val objRef: ObjectReference = myEvaluatedQualifier.asInstanceOf[ObjectReference]
            objRef.setValue(myEvaluatedField, value)
          }
        }

        def getExpectedType: Type = {
          myEvaluatedField.`type`
        }

        def getInspectItem(project: Project): NodeDescriptorImpl = {
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