package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ParameterizedType, TypeSystem}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
  * @author adkozlov
  */
abstract class TwoTypesNamesProvider extends GenericTypeNamesProvider {

  protected val separator: String

  override def isValid(`type`: ScType)(implicit project: Project): Boolean =
    `type` match {
      case ParameterizedType(_, Seq(_, _)) => true
      case _ => false
    }

  override protected def names(designator: ScType, arguments: Seq[ScType])
                              (implicit project: Project): Seq[String] = {
    val Seq(first, second) = arguments
    NameSuggester.compoundNames(argumentNames(first), argumentNames(second), separator)
  }
}

class EitherTypeNamesProvider extends TwoTypesNamesProvider {

  override protected val separator: String = "Or"

  override def isValid(`type`: ScType)(implicit project: Project): Boolean =
    super.isValid(`type`) &&
      (`type`.asInstanceOf[ParameterizedType].designator.canonicalText == "_root_.scala.util.Either")
}

class FromTypeToTypeNamesProvider extends TwoTypesNamesProvider {

  override protected val separator: String = "To"

  override def isValid(`type`: ScType)(implicit project: Project): Boolean =
    super.isValid(`type`) &&
      (GenericTypeNamesProvider.isInheritor(`type`, "scala.collection.GenMap", "java.util.Map") ||
        isFunction1(`type`)(project.typeSystem))

  private def isFunction1(`type`: ScType)(implicit typeSystem: TypeSystem): Boolean =
    `type` match {
      case FunctionType(_, Seq(_)) => true
      case _ => false
    }
}

