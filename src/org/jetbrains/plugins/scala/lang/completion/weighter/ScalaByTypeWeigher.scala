package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import com.intellij.util.text.EditDistance
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.{ScalaAfterNewCompletionUtil, ScalaCompletionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScTypeAlias, ScTypedDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

import scala.collection.mutable


/**
  * Created by kate
  * Suggest type by name where type may be appers. Check on full equality of names, includence one to another
  * or partial alignement
  * on 1/25/16
  */
class ScalaByTypeWeigher extends CompletionWeigher {
  val MAX_DISTANCE = 4
  val EQ_STRINGS = 0
  val INCLUDE = -1
  val LOCAL_FUNC = -5
  val TYPED_DEFINITION = -6

  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val textForPosition = new mutable.HashMap[PsiElement, String]
    val position = ScalaCompletionUtil.positionFromParameters(location.getCompletionParameters)
    val context = location.getProcessingContext

    def extractVariableNameFromPosition: Option[String] = {
      def asBindingPattern: Option[String] = {
        val result = Option(PsiTreeUtil.getContextOfType(position, classOf[ScBindingPattern])).map(_.name)
        if (result.isDefined) textForPosition.put(position, result.get)
        result
      }

      def asDeclaration: Option[String] = {
        val resultOption = Option(PsiTreeUtil.getContextOfType(position, classOf[ScTypedDeclaration])).map(_.declaredElements)
        resultOption.flatMap {
          result =>
            if (result.size != 1) None
            else result.headOption.collect {
              case refPattern: ScFieldId =>
                textForPosition.put(position, refPattern.name)
                refPattern.name
            }
        }
      }

      def asPatternDefinition: Option[String] = {
        val patterns = Option(PsiTreeUtil.getContextOfType(position, classOf[ScPatternDefinition])).map(_.pList.patterns)
        patterns.flatMap {
          allPatterns =>
            if (allPatterns.size != 1) None
            else allPatterns.headOption.collect {
              case refPattern: ScReferencePattern =>
                textForPosition.put(position, refPattern.name)
                refPattern.name
            }
        }
      }

      Option(textForPosition.getOrElse(position,
        asPatternDefinition.getOrElse(
          asBindingPattern.getOrElse(
            asDeclaration.orNull))))
    }


    def handleByText(element: PsiNamedElement): Option[Integer] = {

      def computeDistance(element: PsiNamedElement, text: String): Option[Integer] = {

        def testEq(elementText: String, text: String): Boolean = elementText.toUpperCase == text.toUpperCase

        // prevent MAX_DISTANCE be more or equals on of comparing strings
        val maxDist = Math.min(MAX_DISTANCE, Math.ceil(Math.max(text.length, element.getName.length) / 2))

        if (testEq(element.getName, text)) Some(EQ_STRINGS)
        // prevent computing distance on long non including strings
        else if (Math.abs(text.length - element.getName.length) > maxDist) None
        else {
          val distance = EditDistance.optimalAlignment(element.getName, text, false)
          if (distance > maxDist) None
          else Some(-distance)
        }
      }

      def oneSymbolText(elementText: String, text: String): Boolean = elementText.charAt(0) == text.charAt(0).toUpper

      extractVariableNameFromPosition.flatMap {
        text =>
          text.length match {
            case 0 => None
            case 1 if oneSymbolText(element.getName, text) => Some(0)
            case _ => computeDistance(element, text)
          }
      }
    }

    def typedWeight: Option[Integer] = {
      def inFunction(psiElement: PsiElement): Boolean =
        Option(PsiTreeUtil.getParentOfType(psiElement, classOf[ScBlockExpr])).isDefined

      ScalaLookupItem.original(element) match {
        case s: ScalaLookupItem =>
          lazy val byTextResult = handleByText(s.element)
          s.element match {
            case (_: ScTypeAlias) | (_: ScTypeDefinition) | (_: PsiClass) if byTextResult.isDefined => byTextResult
            case ta: ScTypeAlias if ta.isLocal => Some(LOCAL_FUNC)
            case ta: ScTypeAlias => Some(TYPED_DEFINITION)
            case te: ScTypeDefinition if !te.isObject && (te.isLocal || inFunction(te)) => Some(LOCAL_FUNC)
            case te: ScTypeDefinition => Some(TYPED_DEFINITION)
            case te: PsiClass => Some(TYPED_DEFINITION)
            case _ => None
          }
        case _ => None
      }
    }


    def isAfterNew = ScalaAfterNewCompletionUtil.isAfterNew(position, context)
    def isTypeDefiniton = Option(PsiTreeUtil.getParentOfType(position, classOf[ScTypeElement])).isDefined

    if (isAfterNew || isTypeDefiniton) typedWeight.orNull else null
  }
}
