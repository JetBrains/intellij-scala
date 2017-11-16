package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.atteo.evo.inflector.English
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.namesByType

/**
  * @author adkozlov
  */
class TypePluralNamesProvider extends GenericTypeNamesProvider {

  import TypePluralNamesProvider._

  override def names(`type`: ScParameterizedType): Seq[String] = `type` match {
    case IsTraversable(designator, argument) =>
      val argumentNames = argument match {
        case IsTraversable(_, _) => Seq.empty
        case _ => pluralizeNames(argument)
      }

      namesByType(designator) ++ argumentNames
    case _ => Seq.empty
  }

}

object TypePluralNamesProvider {

  private[namesSuggester] def pluralizeNames(`type`: ScType): Seq[String] =
    namesByType(`type`, withPlurals = false, shortVersion = false).map {
      case "x" => "xs"
      case "index" => "indices"
      case string => English.plural(string)
    }

  private object IsTraversable {

    import GenericTypeNamesProvider.isInheritor

    def unapply(arg: ScType): Option[(ScType, ScType)] = arg match {
      case genericType@ScParameterizedType(designator, Seq(argument))
        if designator.canonicalText == "_root_.scala.Array" || isInheritor(genericType, "scala.collection.GenTraversableOnce", "java.lang.Iterable") =>
        Some(designator, argument)
      case _ => None
    }
  }

}
