package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import com.intellij.util.text.EditDistance.optimalAlignment
import org.jetbrains.plugins.scala.lang.completion.{insideTypePattern, positionFromParameters}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScTypeAlias, ScTypedDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * Suggest type by name where type may be appears. Check on full equality of names, includence one to another
 * or partial alignment
 * on 3/2/16
 */
final class ScalaByNameWeigher extends CompletionWeigher {

  import ScalaByNameWeigher._

  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val parameters = location.getCompletionParameters
    val position = positionFromParameters(parameters)

    def handleByText(name: String): Option[Integer] = {
      val maybeNameAtPosition = parameters.getOriginalPosition match {
        case null => None
        case originalPosition => Option(originalPosition.getUserData(TextForPositionKey))
      }

      val maybeName = maybeNameAtPosition match {
        case None =>
          val result = asBindingPattern(position)
            .orElse(afterColonType(position))
            .orElse(afterNew(position))

          result.foreach {
            position.putCopyableUserData(TextForPositionKey, _)
          }

          result
        case result => result
      }

      maybeName.filterNot(_.isEmpty).map {
        case text if text.length == 1 && text(0).toUpper == name(0) => 0
        case text if text.toUpperCase == name.toUpperCase => 0
        case text =>
          computeDistance(name, text) match {
            case -1 => null
            case distance => -distance
          }
      }
    }

    element.getPsiElement match {
      case namedElement@(_: ScTypeAlias |
                         _: ScTypeDefinition |
                         _: PsiClass |
                         _: ScParameter)
        if insideTypePattern.accepts(position, location.getProcessingContext) =>
        handleByText(namedElement.asInstanceOf[PsiNamedElement].getName).orNull
      case _ => null
    }
  }
}

object ScalaByNameWeigher {

  private[this] val MaxDistance = 4
  private val TextForPositionKey = Key.create[String]("text.for.position")

  private def computeDistance(name: String, text: String): Int = {
    // prevent MAX_DISTANCE be more or equals on of comparing strings
    val maxDist = Math.min(MaxDistance, Math.ceil(Math.max(text.length, name.length) / 2))

    // prevent computing distance on long non including strings
    if (Math.abs(text.length - name.length) > maxDist)
      -1
    else {
      val distance = optimalAlignment(name, text, false)
      if (distance > maxDist) -1 else distance
    }
  }

  private def afterNew(place: PsiElement): Option[String] =
    getContextOfType(place, classOf[ScNewTemplateDefinition]) match {
      case null => None
      case newTemplateDefinition =>
        newTemplateDefinition.getContext match {
          case patterDef: ScPatternDefinition =>
            patterDef.bindings.headOption.map(_.name)
          case assignment: ScAssignment =>
            assignment.referenceName
          case _ => None
        }
    }

  private def afterColonType(place: PsiElement): Option[String] =
    place.getContext.getContext.getContext match {
      case typedDeclaration: ScTypedDeclaration =>
        typedDeclaration.declaredElements.headOption.map(_.name)
      case _ => None
    }

  //case label name
  private def asBindingPattern(place: PsiElement): Option[String] =
    place.getContext.getContext.getContext.getContext match {
      case bp: ScBindingPattern => Some(bp.name)
      case _ => None
    }
}
