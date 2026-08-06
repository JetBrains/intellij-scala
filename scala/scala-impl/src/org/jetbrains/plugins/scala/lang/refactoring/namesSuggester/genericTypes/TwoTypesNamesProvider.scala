package org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.genericTypes

import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

abstract class TwoTypesNamesProvider extends GenericTypeNamesProviderBase {

  import GenericTypeNamesProviderBase.argumentNames

  override protected def isValid(`type`: ScParameterizedType): Boolean =
    `type`.typeArguments.length == 2

  override protected def firstNames(designator: ScType, arguments: Seq[ScType]): Seq[String] =
    argumentNames(arguments)

  override protected def secondNames(designator: ScType, arguments: Seq[ScType]): Seq[String] =
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
        case _ => isInheritor(`type`,
          "scala.collection.GenMap",
          "scala.collection.Map", // Gen* collection types have been removed since 2.13.0
          "scala.collection.MapView", // View type hierarchy was changed in 2.13.0: https://docs.scala-lang.org/overviews/core/collections-migration-213.html
          "java.util.Map",
        )
      })
}
