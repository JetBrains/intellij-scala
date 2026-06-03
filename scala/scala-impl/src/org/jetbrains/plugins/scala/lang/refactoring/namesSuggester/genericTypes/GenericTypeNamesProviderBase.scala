package org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.genericTypes

import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.namesByType

abstract class GenericTypeNamesProviderBase extends GenericTypeNamesProvider {

  import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester._

  override def names(`type`: ScParameterizedType): Seq[String] =
    if (isValid(`type`)) {
      val ParameterizedType(designator, arguments) = `type`
      val firstNames = this.firstNames(designator, arguments)
      val secondNames = this.secondNames(designator, arguments)
      namesByType(designator) ++ compoundNames(firstNames, secondNames, separator)
    }
    else Seq.empty

  protected def isValid(`type`: ScParameterizedType): Boolean

  protected val separator: String = ""

  protected def firstNames(designator: ScType, arguments: Seq[ScType]): Seq[String]

  protected def secondNames(designator: ScType, arguments: Seq[ScType]): Seq[String]
}

object GenericTypeNamesProviderBase {

  private[genericTypes] def argumentNames(arguments: Seq[ScType], index: Int = 0): Seq[String] =
    namesByType(arguments(index), shortVersion = false)
}