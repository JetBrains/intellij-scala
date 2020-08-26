package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType

/**
  * @author adkozlov
  */
class FunctionTypeNamesProvider extends GenericTypeNamesProvider {

  import FunctionTypeNamesProvider.default

  override def names(`type`: ScParameterizedType): collection.Seq[String] = {
    `type` match {
      case FunctionType(returnType, Seq()) =>
        default +: NameSuggester.namesByType(returnType)
      case FunctionType(_, Seq(_, _, _*)) => Seq(default)
      case _ => Seq.empty
    }
  }
}

object FunctionTypeNamesProvider {

  private val default: String = "function"
}
