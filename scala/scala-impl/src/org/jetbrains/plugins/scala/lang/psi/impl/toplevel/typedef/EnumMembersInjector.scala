package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, Model, StringsExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumSingletonCase}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.EnumMembersInjector.methodsForCompanionObject

/**
 * Injects synthetic methods into companion objects of enums: `values`, `valueOf`, `fromOrdinal`.
 */
class EnumMembersInjector extends SyntheticMembersInjector {
  private[this] def companionEnum(obj: ScObject): Option[ScEnum] =
    obj.fakeCompanionClassOrCompanionClass match {
      case enum: ScEnum          => Some(enum)
      case _                     => None
    }

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject =>
      companionEnum(obj).fold(Seq.empty[String]) { enum =>
        val singletonCases = enum.cases.filterByType[ScEnumSingletonCase]
        methodsForCompanionObject(enum, singletonCases)
      }
    case _ => Seq.empty
  }

  override def needsCompanionObject(source: ScTypeDefinition): Boolean =
    source.isInstanceOf[ScEnum]
}

object EnumMembersInjector {
  private def methodsForCompanionObject(owner: ScEnum, singletonCases: Seq[ScEnumCase]): Seq[String] = {
    val tps = owner.typeParameters

    val wildcardsText   =
      if (tps.isEmpty) ""
      else             tps.map(_ => "_").commaSeparated(model = Model.SquareBrackets)

    val rawEnumTypeText = s"${owner.name}$wildcardsText"
    val fromOrdinal     = s"def fromOrdinal(ordinal: _root_.scala.Int): $rawEnumTypeText = ???"

    // @TODO: valueOf return type is actually LUB of all singleton cases
    if (singletonCases.size == owner.cases.size)
      Seq(
        s"def values: _root_.scala.Array[$rawEnumTypeText] = ???",
        s"def valueOf(name: _root_.scala.Predef.String): $rawEnumTypeText = ???",
        fromOrdinal
      )
    else Seq(fromOrdinal)
  }
}
