package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{ClassObjectEvaluator, Evaluator, FieldEvaluator, TypeEvaluator}
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}
import com.sun.jdi.ClassObjectReference
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.project.ProjectExt

class ClassOfEvaluator(tpe: ScType) extends Evaluator {

  import ClassOfEvaluator._

  override def evaluate(context: EvaluationContextImpl): ClassObjectReference =
    prepareEvaluator(tpe)(context)

  private def prepareEvaluator(raw: ScType): EvaluationContextImpl => ClassObjectReference = {
    val tpe = inReadAction(raw.removeAliasDefinitions())

    val stdTypes = tpe.getProject.stdTypes
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
        val vs = if (v.is[String]) s""""$v"""" else v
        val t = inReadAction(lt.removeAliasDefinitions().widenIfLiteral)
        throw EvaluationException(DebuggerBundle.message("error.literal.type.is.not.class.type", vs, t))
      case _ =>
        val jvmName = inReadAction(DebuggerUtil.getJVMQualifiedName(tpe))
        classObjectEvaluator(jvmName)
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
