package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.atteo.evo.inflector.English
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType}

/**
  * @author adkozlov
  */
class TypePluralNamesProvider extends GenericTypeNamesProvider {

  import TypePluralNamesProvider._

  override def names(`type`: ScType): Seq[String] =
    `type` match {
      case JavaArrayType(argument) => pluralizeNames(argument)
      case _: ParameterizedType => super.names(`type`)
      case _ => Seq.empty
    }

  override protected def names(designator: ScType, arguments: Seq[ScType]): Seq[String] =
    pluralizeNames(arguments.head)

  override def isValid(`type`: ScType): Boolean =
    `type` match {
      case _: JavaArrayType => true
      case ParameterizedType(designator, Seq(_)) =>
        designator.canonicalText == "_root_.scala.Array" ||
          GenericTypeNamesProvider.isInheritor(`type`, "scala.collection.GenTraversableOnce", "java.lang.Iterable")
      case _ => false
    }
}

object TypePluralNamesProvider {

  private def pluralizeNames(`type`: ScType): Seq[String] =
    NameSuggester.namesByType(`type`, withPlurals = false, shortVersion = false)
      .map(plural)

  private[this] def plural: String => String = {
    case "x" => "xs"
    case "index" => "indices"
    case string => English.plural(string)
  }
}
