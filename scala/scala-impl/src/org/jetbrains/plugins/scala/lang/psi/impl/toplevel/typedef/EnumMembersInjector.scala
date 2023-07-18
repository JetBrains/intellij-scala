package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.extensions.{Model, StringsExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScEnumCaseImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.EnumMembersInjector.{injectEnumCase, methodsForCompanionObject}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * Injects synthetic methods into generated companion object of enum
 * classes (`values`, `valueOf`, `fromOrdinal`) as well as translates
 * `ScEnumCase`s into proper vals/classes.
 */
class EnumMembersInjector extends SyntheticMembersInjector {
  private[this] def processEnumCases(scEnum: ScEnum): Seq[String] =
    scEnum.cases.map(injectEnumCase)

  override def injectMembers(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject =>
      obj.fakeCompanionClassOrCompanionClass match {
        case scEnum: ScEnum        => processEnumCases(scEnum)
        case ScEnum.Original(enum) => processEnumCases(enum)
        case _                     => Seq.empty
      }
    case _ => Seq.empty
  }

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject => obj.fakeCompanionClassOrCompanionClass match {
      case ScEnum.Original(enum) =>
        val singletonCases =
          enum.cases.collect { case cse @ ScEnumCase.SingletonCase(_, _) => cse }
        methodsForCompanionObject(enum, singletonCases)
      case _ => Seq.empty
    }
    case _ => Seq.empty
  }

  override def needsCompanionObject(source: ScTypeDefinition): Boolean =
    ScEnum.isDesugaredEnumClass(source)
}

object EnumMembersInjector {
  private def superTypesText(superTypes: Seq[ScType]): String =
    superTypes.map(_.canonicalText).mkString(" with ")

  private def injectEnumCase(cse: ScEnumCase): String = {
    val supersText    = superTypesText(cse.superTypes)
    val modifiers     = cse.asInstanceOf[ScEnumCaseImpl].modifierListText

    cse.constructor match {
      case Some(cons) =>
        val tps = cse.typeParameters

        val typeParamsText  =
          if (tps.isEmpty) ""
          else             tps.map(_.typeParameterText).commaSeparated(model = Model.SquareBrackets)

        s"$modifiers final case class ${cse.name}$typeParamsText${cons.getText} extends $supersText"
      case None =>
        val separator = if (cse.name.lastOption.exists(c => !c.isLetterOrDigit && c != '`')) " " else ""
        s"$modifiers val ${cse.name}$separator: $supersText = ???"
    }
  }

  private def methodsForCompanionObject(owner: ScEnum, singletonCases: Seq[ScEnumCase]): Seq[String] = {
    val tps = owner.typeParameters

    val wildcardsText   =
      if (tps.isEmpty) ""
      else             tps.map(_ => "_").commaSeparated(model = Model.SquareBrackets)

    val rawEnumTypeText = s"${owner.name}$wildcardsText"
    val fromOrdinal     = s"def fromOrdinal(ordinal: Int): $rawEnumTypeText = ???"

    // @TODO: valueOf return type is actually LUB of all singleton cases
    if (singletonCases.size == owner.cases.size)
      Seq(
        s"def values: Array[$rawEnumTypeText] = ???",
        s"def valueOf(name: String): $rawEnumTypeText = ???",
        fromOrdinal
      )
    else Seq(fromOrdinal)
  }
}
