package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

/**
  * @author adkozlov
  */
abstract class TwoTypesNamesProvider extends GenericTypeNamesProviderBase {

  import GenericTypeNamesProviderBase.argumentNames

  override protected def isValid(`type`: ScParameterizedType): Boolean =
    `type`.typeArguments.length == 2

  override protected def firstNames(designator: ScType, arguments: collection.Seq[ScType]): collection.Seq[String] =
    argumentNames(arguments)

  override protected def secondNames(designator: ScType, arguments: collection.Seq[ScType]): collection.Seq[String] =
    argumentNames(arguments, 1)
}

class EitherTypeNamesProvider extends TwoTypesNamesProvider {

  override protected val separator: String = "Or"

  override protected def isValid(`type`: ScParameterizedType): Boolean =
    super.isValid(`type`) &&
      (`type`.designator.canonicalText == "_root_.scala.util.Either")
}

class FromTypeToTypeNamesProvider extends TwoTypesNamesProvider {

  override protected val separator: String = "To"

  import GenericTypeNamesProvider.isInheritor

  override protected def isValid(`type`: ScParameterizedType): Boolean =
    super.isValid(`type`) &&
      (`type` match {
        case FunctionType(_, Seq(_)) => true
        case _ => isInheritor(`type`, "scala.collection.GenMap", "java.util.Map")
      })
}

