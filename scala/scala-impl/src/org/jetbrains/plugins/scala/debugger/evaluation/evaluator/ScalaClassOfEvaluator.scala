package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{ClassObjectEvaluator, Evaluator, FieldEvaluator, TypeEvaluator}
import com.sun.jdi.{ClassObjectReference, ObjectReference}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

class ScalaClassOfEvaluator(tpe: ScType) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): ObjectReference =
    prepareEvaluator(tpe)(context)

  private def prepareEvaluator(raw: ScType): EvaluationContextImpl => ObjectReference = {
    val tpe = inReadAction(raw.removeAliasDefinitions())

    val stdTypes = tpe.projectContext.stdTypes
    import stdTypes._

    tpe match {
      case Any | AnyRef | AnyVal | Singleton => classObjectEvaluator("java.lang.Object")
      case Null => classObjectEvaluator("scala.runtime.Null$")
      case Nothing => classObjectEvaluator("scala.runtime.Nothing$")
      case Unit => primitiveClassEvaluator("java.lang.Void")
      case Boolean => primitiveClassEvaluator("java.lang.Boolean")
      case Byte => primitiveClassEvaluator("java.lang.Byte")
      case Char => primitiveClassEvaluator("java.lang.Character")
      case Double => primitiveClassEvaluator("java.lang.Double")
      case Float => primitiveClassEvaluator("java.lang.Float")
      case Int => primitiveClassEvaluator("java.lang.Integer")
      case Long => primitiveClassEvaluator("java.lang.Long")
      case Short => primitiveClassEvaluator("java.lang.Short")
      case lt: ScLiteralType =>
        val v = lt.value.value
        val vs = if (v.is[String]) s""""$v"""" else v
        val t = inReadAction(lt.removeAliasDefinitions().widenIfLiteral)
        throw EvaluationException(ScalaBundle.message("error.literal.type.is.not.class.type", vs, t))
      case _ =>
        val jvmName = inReadAction {
          tpe.extractClass
            .collect {
              case td: ScTypeDefinition =>
                val suffix = if (td.isObject) "$" else ""
                td.getQualifiedNameForDebugger + suffix
              case c => c.qualifiedName
            }
        }.getOrElse(throw EvaluationException(ScalaBundle.message("error.could.not.find.runtime.class", tpe)))
        classObjectEvaluator(jvmName)
    }
  }

  private def classObjectEvaluator(qualifiedName: String)(context: EvaluationContextImpl): ClassObjectReference =
    new ClassObjectEvaluator(new TypeEvaluator(JVMNameUtil.getJVMRawText(qualifiedName)))
      .evaluate(context)
      .asInstanceOf[ClassObjectReference]

  private def primitiveClassEvaluator(qualifiedName: String)(context: EvaluationContextImpl): ClassObjectReference =
    new FieldEvaluator(new TypeEvaluator(JVMNameUtil.getJVMRawText(qualifiedName)), FieldEvaluator.TargetClassFilter.ALL, "TYPE")
      .evaluate(context)
      .asInstanceOf[ClassObjectReference]
}
