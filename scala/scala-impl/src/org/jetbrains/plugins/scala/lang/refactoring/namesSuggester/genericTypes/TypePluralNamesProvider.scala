package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.atteo.evo.inflector.English
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.namesByType

/**
  * @author adkozlov
  */
class TypePluralNamesProvider extends GenericTypeNamesProvider {

  import TypePluralNamesProvider._

  override def names(`type`: ScParameterizedType): collection.Seq[String] = `type` match {
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

  private[namesSuggester] def pluralizeNames(`type`: ScType): collection.Seq[String] =
    namesByType(`type`, withPlurals = false, shortVersion = false).map {
      case letter@IsLetter() => letter + "s"
      case string => English.plural(string)
    }

  private object IsLetter {

    def unapply(string: String): Boolean = string.length == 1 && {
      val character = string(0)
      character.isLetter && character.isLower
    }
  }

  private object IsTraversable {

    import GenericTypeNamesProvider.isInheritor

    def unapply(`type`: ScParameterizedType): Option[(ScType, ScType)] = `type` match {
      case ParameterizedType(designator, Seq(argument))
        if designator.canonicalText == "_root_.scala.Array" ||
          isInheritor(`type`, "scala.collection.GenTraversableOnce", "java.lang.Iterable") =>
        Some(designator, argument)
      case _ => None
    }
  }

}
