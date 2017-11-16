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

  import GenericTypeNamesProvider.isInheritor
  import TypePluralNamesProvider.pluralizeNames

  override def names(`type`: ScParameterizedType): Seq[String] = {
    val designator = `type`.designator
    `type`.typeArguments match {
      case Seq(head) if designator.canonicalText == "_root_.scala.Array" ||
        isInheritor(`type`, "scala.collection.GenTraversableOnce", "java.lang.Iterable") =>
        namesByType(designator) ++ pluralizeNames(head)
      case _ => Seq.empty
    }
  }
}

object TypePluralNamesProvider {

  private[namesSuggester] def pluralizeNames(`type`: ScType): Seq[String] =
    namesByType(`type`, withPlurals = false, shortVersion = false).map {
      case "x" => "xs"
      case "index" => "indices"
      case string => English.plural(string)
    }
}
