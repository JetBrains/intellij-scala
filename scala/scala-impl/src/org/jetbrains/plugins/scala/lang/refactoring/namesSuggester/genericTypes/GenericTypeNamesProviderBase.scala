package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.namesByType

abstract class GenericTypeNamesProviderBase extends GenericTypeNamesProvider {

  import NameSuggester._

  override def names(`type`: ScParameterizedType): collection.Seq[String] =
    if (isValid(`type`)) {
      val ParameterizedType(designator, arguments) = `type`
      val firstNames = this.firstNames(designator, arguments)
      val secondNames = this.secondNames(designator, arguments)
      namesByType(designator) ++ compoundNames(firstNames, secondNames, separator)
    }
    else Seq.empty

  protected def isValid(`type`: ScParameterizedType): Boolean

  protected val separator: String = ""

  protected def firstNames(designator: ScType, arguments: collection.Seq[ScType]): collection.Seq[String]

  protected def secondNames(designator: ScType, arguments: collection.Seq[ScType]): collection.Seq[String]
}

object GenericTypeNamesProviderBase {

  private[genericTypes] def argumentNames(arguments: collection.Seq[ScType], index: Int = 0): collection.Seq[String] =
    namesByType(arguments(index), shortVersion = false)
}