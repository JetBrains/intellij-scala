package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, MacroImpl, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScSymbolLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}

/**
  * Generates accessor types for shapeless LabelledGeneric
  *
  * Extracts type of case class parameter by it's name that has previously been serialized into compound type by
  * [[ShapelessDefaultSymbolicLabelling]]
  */
object ShapelessMkSelector extends ScalaMacroTypeable with ShapelessUtils {

  override val boundMacro: Seq[MacroImpl] = MacroImpl("mkSelector", "shapeless.ops.record.Selector") :: Nil

  private def findValType(name: String)(labelled: ScType): Option[ScType] = {
    def extractKey(tp: ScCompoundType): Option[String] = tp.components.last match {
      case ParameterizedType(des, Seq(tp: ScCompoundType)) if des.canonicalText == fqTagged =>
        tp.typesMap.headOption.map(_._1)
      case _ => None
    }
    def doFind(tp: ScType): Option[ScType] = tp match {
      case ParameterizedType(des, args) if des.canonicalText == fqSelector =>
        doFind(args.head)
      case ParameterizedType(des, Seq(left, right)) if des.canonicalText == fqColonColon =>
        doFind(left).orElse(doFind(right))
      case ParameterizedType(des, Seq(l: ScCompoundType, _)) if des.canonicalText == fqFieldType =>
        doFind(l.components.last)
      case ParameterizedType(des, Seq(l: ScCompoundType, r)) if des.canonicalText == fqKeyTag =>
        if (extractKey(l).contains(name)) Some(r) else None
      case t: ScCompoundType =>
        findValType(name)(t.components.last)
      case _ => None
    }
    doFind(labelled)
  }

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    if (context.expectedType.isEmpty) return None

    val name = context.place match {
      case ScMethodCall(_, Seq(ScSymbolLiteral(value))) => Some(value.name)
      case _ => None
    }
    if (name.isEmpty) return None
    val vt = findValType(name.get)(context.expectedType.get)
    val Out  = vt.map(_.canonicalText).getOrElse("Any")
    ScalaPsiElementFactory
      .createTypeFromText(s"${context.expectedType.get.canonicalText}{type Out = $Out}", context.place, null)
  }
}
