package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import com.intellij.util.text.EditDistance
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.{ScalaAfterNewCompletionUtil, ScalaCompletionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScTypeAlias, ScTypedDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.collection.mutable


/**
  * Created by kate
  * Suggest type by name where type may be appers. Check on full equality of names, includence one to another
  * or partial alignement
  * on 3/2/16
  */
class ScalaByNameWeigher extends CompletionWeigher {
  val MAX_DISTANCE = 4

  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val textForPosition = new mutable.HashMap[PsiElement, String]
    val position = ScalaCompletionUtil.positionFromParameters(location.getCompletionParameters)
    val context = location.getProcessingContext

    def extractVariableNameFromPosition: Option[String] = {
      def afterColonType: Option[String] = {
        val result = position.getContext.getContext.getContext
        result match {
          case typedDeclaration: ScTypedDeclaration =>
            val result = typedDeclaration.declaredElements.headOption.map(_.name)
            if (result.isDefined) textForPosition.put(position, result.get)
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
            textForPosition.put(position, name)
            Some(name)
          case _ => None
        }
      }

      def afterNew: Option[String] = {
        val newTemplateDefinition = Option(PsiTreeUtil.getContextOfType(position, classOf[ScNewTemplateDefinition]))
        val result = newTemplateDefinition.map(_.getContext).flatMap {
          case patterDef: ScPatternDefinition =>
            patterDef.bindings.headOption.map(_.name)
          case assignement: ScAssignStmt =>
            assignement.assignName
          case _ => None
        }

        if (result.isDefined) textForPosition.put(position, result.get)
        result
      }

      Option(textForPosition.getOrElse(position,
        afterColonType.getOrElse(
          asBindingPattern.getOrElse(
            afterNew.orNull))))
    }


    def handleByText(element: PsiNamedElement): Option[Integer] = {

      def computeDistance(element: PsiNamedElement, text: String): Option[Integer] = {

        def testEq(elementText: String, text: String): Boolean = elementText.toUpperCase == text.toUpperCase

        // prevent MAX_DISTANCE be more or equals on of comparing strings
        val maxDist = Math.min(MAX_DISTANCE, Math.ceil(Math.max(text.length, element.getName.length) / 2))

        if (testEq(element.getName, text)) Some(0)
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

    def isAfterNew = ScalaAfterNewCompletionUtil.isAfterNew(position, context)
    def isTypeDefiniton = ScalaCompletionUtil.isTypeDefiniton(position)

    if (isAfterNew || isTypeDefiniton) {
      ScalaLookupItem.original(element) match {
        case s: ScalaLookupItem =>
          lazy val byTextResult = handleByText(s.element)
          s.element match {
            case _: ScTypeAlias | _: ScTypeDefinition | _: PsiClass if byTextResult.isDefined => byTextResult.get
            case _ => null
          }
        case _ => null
      }
    } else null
  }
}
