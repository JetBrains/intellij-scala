package org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.genericTypes

import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

class FunctionTypeNamesProvider extends GenericTypeNamesProvider {

  import FunctionTypeNamesProvider.default

  override def names(`type`: ScParameterizedType): Seq[String] = {
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
