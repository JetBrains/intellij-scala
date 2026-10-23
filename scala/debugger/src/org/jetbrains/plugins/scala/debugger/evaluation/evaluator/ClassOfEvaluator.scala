package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{ClassObjectEvaluator, Evaluator, FieldEvaluator, TypeEvaluator}
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}
import com.intellij.util.concurrency.annotations.{RequiresReadLock, RequiresReadLockAbsence}
import com.sun.jdi.ClassObjectReference
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType, ScTypeExt}

class ClassOfEvaluator @RequiresReadLock (rawType: ScType) extends Evaluator {

  import ClassOfEvaluator._

  // Everything derived from the type is computed here, under the read action the evaluator is built in;
  // evaluate() only talks to the debuggee
  private val classObject: EvaluationContextImpl => ClassObjectReference = prepareEvaluator(rawType.removeAliasDefinitions())

  //noinspection ApiStatus,UnstableApiUsage
  @RequiresReadLockAbsence
  override def evaluate(context: EvaluationContextImpl): ClassObjectReference = classObject(context)

  private def prepareEvaluator(tpe: ScType): EvaluationContextImpl => ClassObjectReference = {
    val stdTypes = tpe.projectContext.stdTypes
    import stdTypes._

    tpe match {
      case Any | AnyRef | AnyVal | Singleton => classObjectEvaluator("java.lang.Object".toJVMName)
      case Null => classObjectEvaluator("scala.runtime.Null$".toJVMName)
      case Nothing => classObjectEvaluator("scala.runtime.Nothing$".toJVMName)
      case Unit => primitiveClassEvaluator("java.lang.Void".toJVMName)
      case Boolean => primitiveClassEvaluator("java.lang.Boolean".toJVMName)
      case Byte => primitiveClassEvaluator("java.lang.Byte".toJVMName)
      case Char => primitiveClassEvaluator("java.lang.Character".toJVMName)
      case Double => primitiveClassEvaluator("java.lang.Double".toJVMName)
      case Float => primitiveClassEvaluator("java.lang.Float".toJVMName)
      case Int => primitiveClassEvaluator("java.lang.Integer".toJVMName)
      case Long => primitiveClassEvaluator("java.lang.Long".toJVMName)
      case Short => primitiveClassEvaluator("java.lang.Short".toJVMName)
      case lt: ScLiteralType =>
        val v = lt.value.value
        val vs = if (v.isInstanceOf[String]) s""""$v"""" else v
        val t = lt.removeAliasDefinitions().widenIfLiteral
        throw EvaluationException(DebuggerBundle.message("error.literal.type.is.not.class.type", vs, t))
      case _ =>
        classObjectEvaluator(DebuggerUtil.getJVMQualifiedName(tpe))
    }
  }

  private def classObjectEvaluator(name: JVMName)(context: EvaluationContextImpl): ClassObjectReference =
    new ClassObjectEvaluator(new TypeEvaluator(name)).evaluate(context).asInstanceOf[ClassObjectReference]

  private def primitiveClassEvaluator(name: JVMName)(context: EvaluationContextImpl): ClassObjectReference =
    new FieldEvaluator(new TypeEvaluator(name), FieldEvaluator.TargetClassFilter.ALL, "TYPE")
      .evaluate(context)
      .asInstanceOf[ClassObjectReference]
}

private object ClassOfEvaluator {
  implicit class StringToJVMNameOps(private val raw: String) extends AnyVal {
    def toJVMName: JVMName = JVMNameUtil.getJVMRawText(raw)
  }
}
