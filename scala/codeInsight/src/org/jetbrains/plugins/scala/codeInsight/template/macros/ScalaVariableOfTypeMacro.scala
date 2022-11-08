package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.java.JavaBundle
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.template.util.VariablesCompletionProcessor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState, StdKinds}

sealed abstract class ScalaVariableOfTypeMacro extends ScalaMacro {

  import ScalaVariableOfTypeMacro._

  override def calculateLookupItems(expressions: Array[Expression], context: ExpressionContext): Array[LookupElement] = expressions match {
    case _ if arrayIsValid(expressions) =>
      implicit val c: ExpressionContext = context
      calculateLookups(expressions.map(calculate))
    case _ => null
  }

  override def calculateResult(expressions: Array[Expression], context: ExpressionContext): Result =
    expressions match {
      case _ if arrayIsValid(expressions) =>
        implicit val c: ExpressionContext = context
        val maybeResult = findDefinitions.collectFirst {
          case (typed, scType) if typeText(expressions, scType) => new TextResult(typed.name)
        }

        maybeResult.orNull
      case _ => null
    }

  def calculateLookups(expressions: Array[String],
                       showOne: Boolean = false)
                      (implicit context: ExpressionContext): Array[LookupElement] = {
    val elements = for {
      (typed, scType) <- findDefinitions
      typeText <- this.typeText(expressions, scType)
    } yield {
      LookupElementBuilder.create(typed, typed.name)
        .withTypeText(typeText)
    }

    elements match {
      case Nil | _ :: Nil if !showOne => null
      case _                          => elements.toArray
    }
  }

  override def calculateQuickResult(p1: Array[Expression], p2: ExpressionContext): Result = null

  override def getDefaultValue: String = "x"

  def arrayIsValid(array: Array[_]): Boolean = array.isEmpty

  protected def typeText(expressions: Array[Expression], `type`: ScType)
                        (implicit context: ExpressionContext): Boolean = {
    val text = typeText(expressions.map(calculate), `type`)
    text.isDefined
  }

  protected def typeText(expressions: Array[String], `type`: ScType): Option[String] = expressions match {
    case Array("", _*) => Some(`type`.presentableText(TypePresentationContext.emptyContext))
    case Array(IterableId, _*) =>
      if (isArray(`type`) || isIterable(`type`)) {
        Some(null)
      } else {
        None
      }
    case array if array.contains(`type`.extractClass.fold("")(_.qualifiedName)) =>
      Some(null)
    case _ => None
  }
}

object ScalaVariableOfTypeMacro {

  val IterableId = "foreach"

  /**
    * This class provides macros for live templates. Return elements
    * of given class type (or class types).
    */
  final class RegularVariable extends ScalaVariableOfTypeMacro {

    override def getNameShort: String = "variableOfType"

    override def getPresentableName: String = JavaBundle.message("macro.variable.of.type")

    override def arrayIsValid(array: Array[_]): Boolean = array.nonEmpty
  }

  final class ArrayVariable extends ScalaVariableOfTypeMacro {

    override def getNameShort: String = "arrayVariable"

    private val expressions = Array("scala.Array")

    override protected def typeText(expressions: Array[String], `type`: ScType): Option[String] =
      super.typeText(this.expressions, `type`)

    override protected def typeText(expressions: Array[Expression], `type`: ScType)
                                   (implicit context: ExpressionContext): Boolean =
      super.typeText(this.expressions.map(new TextExpression(_)), `type`)
  }

  final class IterableVariable extends ScalaVariableOfTypeMacro {

    override def getNameShort: String = "iterableVariable"

    private val expressions = Array(IterableId)

    override protected def typeText(expressions: Array[String], `type`: ScType): Option[String] =
      super.typeText(this.expressions, `type`)

    override protected def typeText(expressions: Array[Expression], `type`: ScType)
                                   (implicit context: ExpressionContext): Boolean =
      super.typeText(this.expressions.map(new TextExpression(_)), `type`)
  }

  private[macros] def isIterable(`type`: ScType) = `type`.extractClass.exists {
    case definition: ScTypeDefinition => definition.allFunctionsByName(IterableId).nonEmpty
    case _ => false
  }

  private def findDefinitions(implicit context: ExpressionContext) = findElementAtOffset match {
    case Some(element) =>
      variablesForScope(element).collect {
        case ScalaResolveResult(definition: ScTypeDefinition, _) if isFromScala(definition) => definition
      }.collect {
        case definition@Typeable(scType) => (definition, scType)
      }
    case _ => Nil
  }

  /**
    * @param element from which position we look at locals
    * @return visible variables and values from element position
    */
  private[this] def variablesForScope(element: PsiElement) = {
    val processor = new VariablesCompletionProcessor(StdKinds.valuesRef)(element)
    PsiTreeUtil.treeWalkUp(processor, element, null, ScalaResolveState.empty)
    processor.candidates.toList
  }

  private[this] def isFromScala(definition: ScTypeDefinition) =
    PsiTreeUtil.getParentOfType(definition, classOf[PsiClass]) match {
      case ClassQualifiedName("scala.Predef" | "scala") => false
      case _ => true
    }

  private[macros] def calculate(expression: Expression)
                               (implicit context: ExpressionContext): String =
    expression.calculateResult(context).toString
}