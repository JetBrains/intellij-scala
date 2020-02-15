package org.jetbrains.plugins.scala
package lang
package completion
package weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.EditDistance.optimalAlignment
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScTypeAlias, ScTypedDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * Created by kate
 * Suggest type by name where type may be appears. Check on full equality of names, includence one to another
 * or partial alignment
 * on 3/2/16
 */
final class ScalaByNameWeigher extends CompletionWeigher {

  import ScalaByNameWeigher._

  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val position = positionFromParameters(location.getCompletionParameters)
    val originalPosition = location.getCompletionParameters.getOriginalPosition

    def extractVariableNameFromPosition: Option[String] = {
      def afterColonType: Option[String] = {
        val result = position.getContext.getContext.getContext
        result match {
          case typedDeclaration: ScTypedDeclaration =>
            val result = typedDeclaration.declaredElements.headOption.map(_.name)
            if (result.isDefined) position.putUserData(TextForPositionKey, result.get)
            result
          case _ => None
        }
      }

      //case label name
      def asBindingPattern: Option[String] = {
        val result = position.getContext.getContext.getContext.getContext
        result match {
          case bp: ScBindingPattern =>
            val name = bp.name
            position.putUserData(TextForPositionKey, name)
            Some(name)
          case _ => None
        }
      }

      def afterNew: Option[String] = {
        val newTemplateDefinition = Option(PsiTreeUtil.getContextOfType(position, classOf[ScNewTemplateDefinition]))
        val result = newTemplateDefinition.map(_.getContext).flatMap {
          case patterDef: ScPatternDefinition =>
            patterDef.bindings.headOption.map(_.name)
          case assignment: ScAssignment =>
            assignment.referenceName
          case _ => None
        }

        if (result.isDefined) position.putUserData(TextForPositionKey, result.get)
        result
      }

      Option(originalPosition)
        .flatMap(_.getUserData(TextForPositionKey).toOption)
        .orElse(asBindingPattern)
        .orElse(afterColonType)
        .orElse(afterNew)
    }


    def handleByText(name: String): Option[Integer] =
      extractVariableNameFromPosition.flatMap {
        case "" => None
        case text if text.length == 1 && text(0).toUpper == name(0) => Some(0)
        case text => computeDistance(name, text)
      }

    element match {
      case ScalaLookupItem(_, namedElement) if insideTypePattern.accepts(position, location.getProcessingContext) =>
        namedElement match {
          case _: ScTypeAlias |
               _: ScTypeDefinition |
               _: PsiClass |
               _: ScParameter => handleByText(namedElement.getName).orNull
          case _ => null
        }
      case _ => null
    }
  }
}

object ScalaByNameWeigher {

  private[this] val MaxDistance = 4
  private val TextForPositionKey = Key.create[String]("text.for.position")

  private def computeDistance(name: String, text: String): Option[Integer] = {
    // prevent MAX_DISTANCE be more or equals on of comparing strings
    val maxDist = Math.min(MaxDistance, Math.ceil(Math.max(text.length, name.length) / 2))

    if (name.toUpperCase == text.toUpperCase) Some(0)
    // prevent computing distance on long non including strings
    else if (Math.abs(text.length - name.length) > maxDist) None
    else {
      val distance = optimalAlignment(name, text, false)
      if (distance > maxDist) None else Some(-distance)
    }
  }
}
