package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, MacroImpl, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

/**
  * generates HList types without tagging
  *
  * example:
  * case class Foo(age: Int, name: String) ->
  * -> Generic.Aux[Main.this.Foo, shapeless.this.::[Int, shapeless.this.::[String, shapeless.this.HNil]]]
  */
object ShapelessMaterializeGeneric extends ScalaMacroTypeable with ShapelessUtils {

  override val boundMacro: Seq[MacroImpl] = MacroImpl("materialize", "shapeless.Generic") :: Nil

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    if (context.expectedType.isEmpty) return None

    typesInScope(context).map { foundTypes =>
      import foundTypes._

      val targetType = extractTargetType(context)
      val fields     = extractFields(targetType).map(_._2)
      val hList      = hListType(fields, hNil, hCons)

      ScParameterizedType(genericAux, Seq(targetType, hList))
    }
  }

  private def typesInScope(implicit context: MacroContext): Option[TypesInScope] = {
    for {
      hNil   <- findClassType(fqHNil)
      hCons  <- findClassType(fqColonColon)
      aux    <- findAliasProjectionType(fqGeneric, "Aux")
    } yield {
      TypesInScope(hNil, hCons, aux)
    }
  }

  private case class TypesInScope(hNil: ScType, hCons: ScType, genericAux: ScType)
}