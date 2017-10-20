package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TupleType}

/**
  * @author adkozlov
  */
abstract class SyntheticTypeNamesProvider extends GenericTypeNamesProvider {

  protected def default: String

  override final def isValid(`type`: ScType): Boolean =
    isValidImpl(`type`)

  protected def isValidImpl(`type`: ScType): Boolean

  override protected final def names(designator: ScType, arguments: Seq[ScType]): Seq[String] =
    throw new IllegalStateException()
}

class FunctionTypeNamesProvider extends SyntheticTypeNamesProvider {

  override protected def default: String = "function"

  override def names(`type`: ScType): Seq[String] = {
    `type` match {
      case FunctionType(returnType, arguments) =>
        val returnTypeNames = arguments match {
          case Seq() => NameSuggester.namesByType(returnType)
          case _ => Seq.empty
        }
        Seq(default) ++ returnTypeNames
      case _ => Seq.empty
    }
  }

  override def isValidImpl(`type`: ScType): Boolean =
    `type` match {
      case FunctionType(_, Seq() | Seq(_, _, _*)) => true
      case _ => false
    }
}

class TupleTypeNamesProvider extends SyntheticTypeNamesProvider {

  override protected def default: String = "tuple"

  override def names(`type`: ScType): Seq[String] =
    if (isValid(`type`)) Seq(default) else Seq.empty

  override def isValidImpl(`type`: ScType): Boolean =
    `type` match {
      case TupleType(_) => true
      case _ => false
    }
}
