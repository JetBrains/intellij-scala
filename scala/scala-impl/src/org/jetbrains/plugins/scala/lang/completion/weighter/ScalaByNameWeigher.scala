package org.jetbrains.plugins.scala.lang
package completion
package weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.EditDistance
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScTypeAlias, ScTypedDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition


/**
  * Created by kate
  * Suggest type by name where type may be appers. Check on full equality of names, includence one to another
  * or partial alignement
  * on 3/2/16
  */
class ScalaByNameWeigher extends CompletionWeigher {
  private val MAX_DISTANCE = 4
  private val textForPositionKey: Key[String] = Key.create("text.for.position")

  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val position = positionFromParameters(location.getCompletionParameters)
    val originalPostion = location.getCompletionParameters.getOriginalPosition

    def extractVariableNameFromPosition: Option[String] = {
      def afterColonType: Option[String] = {
        val result = position.getContext.getContext.getContext
        result match {
          case typedDeclaration: ScTypedDeclaration =>
            val result = typedDeclaration.declaredElements.headOption.map(_.name)
            if (result.isDefined) position.putUserData(textForPositionKey, result.get)
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
            position.putUserData(textForPositionKey, name)
            Some(name)
          case _ => None
        }
      }

      def afterNew: Option[String] = {
        val newTemplateDefinition = Option(PsiTreeUtil.getContextOfType(position, classOf[ScNewTemplateDefinition]))
        val result = newTemplateDefinition.map(_.getContext).flatMap {
          case patterDef: ScPatternDefinition =>
            patterDef.bindings.headOption.map(_.name)
          case assignement: ScAssignment =>
            assignement.referenceName
          case _ => None
        }

        if (result.isDefined) position.putUserData(textForPositionKey, result.get)
        result
      }

      Option(originalPostion)
        .flatMap(_.getUserData(textForPositionKey).toOption)
        .orElse(asBindingPattern)
        .orElse(afterColonType)
        .orElse(afterNew)
    }


    def handleByText(name: String): Option[Integer] = {
      def computeDistance(text: String): Option[Integer] = {
        // prevent MAX_DISTANCE be more or equals on of comparing strings
        val maxDist = Math.min(MAX_DISTANCE, Math.ceil(Math.max(text.length, name.length) / 2))

        if (name.toUpperCase == text.toUpperCase) Some(0)
        // prevent computing distance on long non including strings
        else if (Math.abs(text.length - name.length) > maxDist) None
        else {
          val distance = EditDistance.optimalAlignment(name, text, false)
          if (distance > maxDist) None else Some(-distance)
        }
      }

      extractVariableNameFromPosition.flatMap {
        case "" => None
        case text if text.length == 1 && text.charAt(0).toUpper == name.charAt(0) => Some(0)
        case text => computeDistance(text)
      }
    }

    val maybeResult = if (ScalaAfterNewCompletionContributor.isInTypeElement(position, Some(location))) {
      element match {
        case ScalaLookupItem(_, namedElement) =>
          namedElement match {
            case _: ScTypeAlias |
                 _: ScTypeDefinition |
                 _: PsiClass |
                 _: ScParameter => handleByText(namedElement.getName)
            case _ => None
          }
        case _ => None
      }
    } else None

    maybeResult.orNull
  }
}
