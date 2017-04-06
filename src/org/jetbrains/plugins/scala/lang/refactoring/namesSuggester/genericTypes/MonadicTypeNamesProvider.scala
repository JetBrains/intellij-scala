package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType

/**
  * @author adkozlov
  */
class MonadicTypeNamesProvider extends GenericTypeNamesProvider {

  import MonadicTypeNamesProvider._

  override protected def names(designator: ScType, arguments: Seq[ScType])
                              (implicit project: Project): Seq[String] =
    NameSuggester.compoundNames(findPrefix(designator).toSeq, argumentNames(arguments.head))

  override def isValid(`type`: ScType)(implicit project: Project): Boolean =
    `type` match {
      case ParameterizedType(designator, Seq(_)) => findPrefix(designator).isDefined
      case _ => false
    }
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
