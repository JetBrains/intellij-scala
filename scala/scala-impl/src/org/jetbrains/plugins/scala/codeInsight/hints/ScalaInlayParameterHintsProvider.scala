package org.jetbrains.plugins.scala
package codeInsight
package hints

import java.{util => ju}

import com.intellij.codeInsight.hints.{Option => HintOption, _}
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.collection.JavaConverters

class ScalaInlayParameterHintsProvider extends InlayParameterHintsProvider {

  import ScalaInlayParameterHintsProvider._

  import JavaConverters._

  override def getSupportedOptions: ju.List[HintOption] =
    HintTypes.map(_.option).asJava

  override def getParameterHints(element: PsiElement): ju.List[InlayInfo] =
    parameterHints(element).asJava

  override def getHintInfo(element: PsiElement): HintInfo =
    hintInfo(element).orNull

  override def getInlayPresentation(inlayText: String): String = inlayText

  override def getDefaultBlackList: ju.Set[String] = DefaultBlackList

  override def getBlackListDependencyLanguage: JavaLanguage = JavaLanguage.INSTANCE
}

object ScalaInlayParameterHintsProvider {

  private val DefaultBlackList = ju.Collections.singleton("scala.*")

  private val HintTypes = List(
    ParameterHintType,
    ReturnTypeHintType,
    PropertyHintType,
    LocalVariableHintType
  )

  def instance: ScalaInlayParameterHintsProvider =
    InlayParameterHintsExtension.INSTANCE.forLanguage(ScalaLanguage.INSTANCE) match {
      case provider: ScalaInlayParameterHintsProvider => provider
    }

  private def parameterHints(element: PsiElement): Seq[InlayInfo] =
    HintTypes.flatMap { hintType =>
      hintType(element)
    }

  private def hintInfo(element: PsiElement): Option[HintInfo] =
    HintTypes.find(_.isDefinedAt(element)).collect {
      case ParameterHintType => ParameterHintType.methodInfo(element)
      case hintType: OptionHintType => hintType
    }

  private[hints] sealed trait HintType extends HintFunction {

    val option: HintOption

    protected val delegate: HintFunction

    override final def isDefinedAt(element: PsiElement): Boolean = delegate.isDefinedAt(element)

    override final def apply(element: PsiElement): Seq[InlayInfo] =
      if (option.isEnabled && isDefinedAt(element)) delegate(element)
      else Seq.empty
  }

  private[hints] case object ParameterHintType extends HintType {

    override val option = HintOption(Seq("parameter", "name"), defaultValue = true)

    def methodInfo(element: PsiElement): HintInfo.MethodInfo = element match {
      case NonSyntheticMethodCall(method) => MethodInfo(method)
      case ConstructorCall(method) => MethodInfo(method)
    }

    override protected val delegate: HintFunction = {
      case call@NonSyntheticMethodCall(_) => withParameters(call, call.matchedParameters.reverse)
      case call@ConstructorCall(_) => withParameters(call, call.matchedParameters)
    }

    private object NonSyntheticMethodCall {

      def unapply(methodCall: ScMethodCall): Option[PsiMethod] = methodCall.deepestInvokedExpr match {
        case ScReferenceExpression(method: PsiMethod) if !method.isParameterless && methodCall.argumentExpressions.nonEmpty => Some(method)
        case _ => None
      }
    }

    private object ConstructorCall {

      def unapply(constructor: ScConstructor): Option[PsiMethod] =
        if (PsiTreeUtil.getContextOfType(constructor, classOf[ScNewTemplateDefinition]) != null &&
          constructor.arguments.nonEmpty) {
          constructor.reference.collect {
            case ResolvesTo(method: PsiMethod) => method
          }
        } else None
    }

    private def withParameters(call: PsiElement,
                               matchedParameters: Seq[(ScExpression, Parameter)]): Seq[InlayInfo] = {
      val (varargs, regular) = matchedParameters.filter {
        case (argument, _) => call.isAncestorOf(argument)
      }.partition {
        case (_, parameter) => parameter.isRepeated
      }

      (regular ++ varargs.headOption).filter {
        case (MethodCall(name), parameter) if name == parameter.name => false
        case (argument, parameter) => isNameable(argument) && isNameable(parameter)
      }.map {
        case (argument, parameter) => InlayInfo(parameter, argument)
      }
    }

    private object MethodCall {

      def unapply(expression: ScExpression): Option[String] = expression match {
        case MethodRepr(_, maybeExpression, maybeReference, Seq()) =>
          maybeReference.orElse(maybeExpression).collect {
            case reference: ScReferenceExpression => reference.refName
          }
        case _ => None
      }
    }

    private def isNameable(argument: ScExpression) =
      argument.parent.collect {
        case list: ScArgumentExprList => list
      }.exists(!_.isBraceArgs)

    private def isNameable(parameter: Parameter) =
      parameter.name.length > 1
  }

  private[hints] abstract class OptionHintType protected(idSegments: String*)
    extends HintInfo.OptionInfo(HintOption(idSegments)) with HintType {

    override val option: HintOption = getOption

    override def enable(): Unit =
      if (!option.get()) {
        option.set(true)
      }

    override def disable(): Unit =
      if (option.get()) {
        option.set(false)
      }
  }

  private[hints] case object ReturnTypeHintType extends OptionHintType("function", "return", "type") {

    override protected val delegate: HintFunction = {
      case function: ScFunction if !function.hasExplicitType =>
        function.returnType.toSeq
          .map(InlayInfo(_, function.parameterList))
    }
  }

  private[hints] abstract class DefinitionHintType(isLocal: Boolean, idSegments: String*)
    extends OptionHintType(idSegments :+ "type": _*) {

    import DefinitionHintType._

    override protected val delegate: HintFunction = {
      case TypelessDefinition(definition, patternList, `isLocal`) =>
        definition.`type`().toSeq
          .map(InlayInfo(_, patternList))
    }
  }

  private[this] object DefinitionHintType {

    private object TypelessDefinition {

      def unapply(element: PsiElement): Option[(ScValueOrVariable, ScPatternList, Boolean)] = element match {
        case definition: ScValueOrVariable if !definition.hasExplicitType =>
          val maybePatternList = definition match {
            case value: ScPatternDefinition => Some(value.pList)
            case variable: ScVariableDefinition => Some(variable.pList)
            case _ => None
          }

          maybePatternList.map((definition, _, definition.isLocal))
        case _ => None
      }
    }

  }

  private[hints] case object PropertyHintType extends DefinitionHintType(isLocal = false, "property")

  private[hints] case object LocalVariableHintType extends DefinitionHintType(isLocal = true, "local", "variable")

}