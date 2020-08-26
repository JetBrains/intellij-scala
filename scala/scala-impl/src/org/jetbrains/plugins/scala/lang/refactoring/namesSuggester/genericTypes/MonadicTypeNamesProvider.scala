package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

/**
  * @author adkozlov
  */
class MonadicTypeNamesProvider extends GenericTypeNamesProviderBase {

  import GenericTypeNamesProviderBase.argumentNames
  import MonadicTypeNamesProvider._

  override protected def isValid(`type`: ScParameterizedType): Boolean =
    `type`.typeArguments.length == 1 &&
      findPrefix(`type`.designator).isDefined

  override protected def firstNames(designator: ScType, arguments: collection.Seq[ScType]): collection.Seq[String] =
    findPrefix(designator).toSeq

  override protected def secondNames(designator: ScType, arguments: collection.Seq[ScType]): collection.Seq[String] =
    argumentNames(arguments)
}

object MonadicTypeNamesProvider {

  private def findPrefix(designator: ScType): Option[String] =
    needPrefix.get(designator.canonicalText)

  private[this] val needPrefix = Map(
    "_root_.scala.Option" -> "maybe",
    "_root_.scala.Some" -> "some",
    "_root_.scala.concurrent.Future" -> "eventual",
    "_root_.scala.concurrent.Promise" -> "promised",
    "_root_.scala.util.Try" -> "tried"
  )
}
