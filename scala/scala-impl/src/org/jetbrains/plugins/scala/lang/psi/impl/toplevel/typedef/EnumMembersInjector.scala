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

  private[this] def companionEnum(obj: ScObject): Option[ScEnum] =
    obj.fakeCompanionClassOrCompanionClass match {
      case enum: ScEnum          => Some(enum)
      case _                     => None
    }

  override def injectMembers(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject => companionEnum(obj).fold(Seq.empty[String])(processEnumCases)
    case _             => Seq.empty
  }

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject =>
      companionEnum(obj).fold(Seq.empty[String]) { enum =>
        val singletonCases =
          enum.cases.collect { case cse @ ScEnumCase.SingletonCase(_, _) => cse }

        methodsForCompanionObject(enum, singletonCases)
      }
    case _ => Seq.empty
  }

  override def needsCompanionObject(source: ScTypeDefinition): Boolean =
    source.isInstanceOf[ScEnum]
}

object EnumMembersInjector {
  private def injectEnumCase(cse: ScEnumCase): String = {
    def supersToString(superTypes: Seq[ScType]): String =
      superTypes.map(_.canonicalText).mkString(" with ")

    val modifiers   = cse.asInstanceOf[ScEnumCaseImpl].modifierListText
    val annotations = cse.asInstanceOf[ScEnumCaseImpl].annotationsText
    val supersText  = supersToString(cse.superTypes)

    cse.constructor match {
      case Some(cons) =>
        val tps = cse.typeParameters

        val typeParamsText  =
          if (tps.isEmpty) ""
          else             tps.map(_.typeParameterText).commaSeparated(model = Model.SquareBrackets)

        s"""$annotations
           |$modifiers final case class ${cse.name}$typeParamsText${cons.getText} extends $supersText {
           |  override def ordinal: Int = ???
           |}""".stripMargin
      case None =>
        val separator =
          if (cse.name.lastOption.exists(c => !c.isLetterOrDigit && c != '`')) " "
          else                                                                 ""

        s"$annotations $modifiers val ${cse.name}$separator: $supersText = ???"
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
