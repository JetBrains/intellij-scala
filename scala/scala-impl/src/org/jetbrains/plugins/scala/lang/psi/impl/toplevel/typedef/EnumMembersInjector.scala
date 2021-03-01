package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.extensions.{Model, StringsExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.EnumMembersInjector.{injectEnumCase, injectEnumMethods}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * Injects synthetic methods into generated companion object of enum
 * classes (`values`, `valueOf`, `fromOrdinal`) as well as translates
 * `ScEnumCase`s into proper vals/classes.
 */
class EnumMembersInjector extends SyntheticMembersInjector {
  private[this] def processEnumCases(enum: ScEnum): Seq[String] =
    enum.cases.map(injectEnumCase)

  override def injectMembers(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject =>
      obj.fakeCompanionClassOrCompanionClass match {
        case enum: ScEnum                    => processEnumCases(enum)
        case ScEnum.DesugaredEnumClass(enum) => processEnumCases(enum)
        case _                               => Seq.empty
      }
    case _ => Seq.empty
  }

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject if obj.isSynthetic => obj.fakeCompanionClassOrCompanionClass match {
      case ScEnum.DesugaredEnumClass(enum) =>
        val singletonCases =
          enum.cases.collect { case cse @ ScEnumCase.SingletonCase(_, _) => cse }
        injectEnumMethods(enum, singletonCases)
      case _ => Seq.empty
    }
    case _ => Seq.empty
  }

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = source match {
    case ScEnum.DesugaredEnumClass(_) => true
    case _                            => false
  }
}

object EnumMembersInjector {
  private def superTypesText(superTypes: Seq[ScType]): String =
    superTypes.map(_.canonicalText).mkString(" with ")

  private def injectEnumCase(cse: ScEnumCase): String = {
    val supersText    = superTypesText(cse.superTypes)
    val modifiers     = cse.getModifierList.getText

    cse.constructor match {
      case Some(cons) =>
        val tps = cse.typeParameters

        val typeParamsText  =
          if (tps.isEmpty) ""
          else             tps.map(_.typeParameterText).commaSeparated(model = Model.SquareBrackets)

        s"$modifiers case class ${cse.name}$typeParamsText${cons.getText} extends $supersText"
      case None =>
        s"$modifiers val ${cse.name}: $supersText = ???"
    }
  }

  private def injectEnumMethods(owner: ScEnum, singletonCases: Seq[ScEnumCase]): Seq[String] = {
    val tps = owner.typeParameters

    val wildcardsText   =
      if (tps.isEmpty) ""
      else             tps.map(_ => "_").commaSeparated(model = Model.SquareBrackets)

    val rawEnumTypeText = s"${owner.name}$wildcardsText"
    val fromOrdinalString = s"def fromOrdinal(ordinal: Int): $rawEnumTypeText = ???"

    // @TODO: valueOf return type is acutually LUB of all singleton cases
    if (singletonCases.size == owner.cases.size)
      Seq(
        s"def values: Array[$rawEnumTypeText] = ???",
        s"def valueOf(name: String): $rawEnumTypeText = ???",
        fromOrdinalString
      )
    else Seq(fromOrdinalString)
  }

}
