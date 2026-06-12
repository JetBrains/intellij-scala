package org.jetbrains.plugins.scala.lang.completion.clauses

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ExtractorMatch
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypePresentation}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.{UniqueNameSuggester, suggestNamesByType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

sealed abstract class PatternComponents {

  def canonicalPatternText: String

  override final def toString: String =
    s"${getClass.getSimpleName}: $canonicalPatternText"
}

object PatternComponents {

  implicit class Ext(private val components: PatternComponents) extends AnyVal {

    def canonicalClauseText(implicit project: Project): String =
      clauseText(components.canonicalPatternText)

    def clauseText(patternText: String)
                  (implicit project: Project): String =
      s"${ScalaKeyword.CASE} $patternText ${ScalaPsiUtil.functionArrow}"
  }
}

sealed abstract class ClassPatternComponents(`class`: PsiClass,
                                             qualifiedName: String,
                                             canonicalSuffix: String)
  extends PatternComponents {

  def this(`class`: PsiClass) = this(
    `class`,
    `class`.qualifiedName,
    `class`.getTypeParameters.length match {
      case 0 => ""
      case length => Seq.fill(length)(Placeholder).commaSeparated(Model.SquareBrackets)
    }
  )

  override final def canonicalPatternText: String =
    namedPatternText(Left(qualifiedName))

  def presentablePatternText(reference: Either[String, ScStableCodeReference] = Left(`class`.name)): String =
    presentablePrefix(reference)

  protected final def namedPatternText(reference: Either[String, ScStableCodeReference]): String = {
    val suggestedName = reference.map {
      _.getParent match {
        case simple: ScSimpleTypeElement => simple
        case infix: ScInfixTypeElement => infix
        case parent => throw new IllegalArgumentException(s"Simple or infix type expected, actual `${parent.getClass}`: ${parent.getText}")
      }
    }.flatMap {
      _.`type`()
    }.flatMap {
      suggestNamesByType(_)
        .headOption
        .toRight(Placeholder)
    }.getOrElse(Placeholder)

    suggestedName + ": " + presentablePrefix(reference) + canonicalSuffix
  }

  private def presentablePrefix(reference: Either[String, ScStableCodeReference]): String =
    reference.fold(identity, _.getText)
}

sealed abstract class SequenceBasedPatternComponents[T](`class`: ScTypeDefinition, components: Iterable[T])
                                                       (function: T => String)
  extends ClassPatternComponents(`class`) {

  protected val presentableSuffix: String = components.map(function).commaSeparated(Model.Parentheses)

  override def presentablePatternText(reference: Either[String, ScStableCodeReference]): String =
    super.presentablePatternText(reference) + presentableSuffix
}

final class TypedPatternComponents(`class`: PsiClass) extends ClassPatternComponents(`class`) {

  override def presentablePatternText(reference: Either[String, ScStableCodeReference]): String =
    namedPatternText(reference)
}

final class InfixCaseClassPatternComponents private(
  `class`: ScConstructorOwner,
  private val firstParameter: ScClassParameter,
  private val secondParameter: ScClassParameter
) extends ClassPatternComponents(`class`) {
  override def presentablePatternText(reference: Either[String, ScStableCodeReference]): String = reference match {
    case Right(ref) if ref.qualifier.isDefined =>
      // similar to CaseClassPatternComponents if the reference is qualified (SCL-25490).
      // `case x foo.bar y => ???` won't compile, render as `case foo.bar(x, y) => ???` instead.
      val presentableSuffix = Seq(firstParameter, secondParameter)
        .map(CaseClassPatternComponents.presentableParameterText)
        .commaSeparated(Model.Parentheses)
      super.presentablePatternText(reference) + presentableSuffix
    case _ =>
      s"${firstParameter.name} ${super.presentablePatternText(reference)} ${secondParameter.name}"
  }
}

object InfixCaseClassPatternComponents {
  def unapply(`class`: ScConstructorOwner): Option[InfixCaseClassPatternComponents] = for {
    constructor <- `class`.constructor
    if `class`.isCase && isInfixLike(`class`)
    (firstParameter, secondParameter) <- InfixParams.unapply(constructor)
  } yield new InfixCaseClassPatternComponents(`class`, firstParameter, secondParameter)

  private def isInfixLike(`class`: ScConstructorOwner): Boolean =
    ScalaNamesUtil.isOperatorName(`class`.name) || `class`.hasModifierPropertyScala(ScalaModifier.INFIX)

  private object InfixParams {
    def unapply(constructor: ScPrimaryConstructor): Option[(ScClassParameter, ScClassParameter)] =
      constructor.effectiveParameterClauses match {
        case Seq(clause) =>
          clause.effectiveParameters match {
            case Seq(firstParameter: ScClassParameter, secondParameter: ScClassParameter) =>
              Some((firstParameter, secondParameter))
            case _ => None
          }
        case _ => None
      }
  }
}

final class CaseClassPatternComponents private(`class`: ScConstructorOwner,
                                               constructor: ScPrimaryConstructor)
  extends SequenceBasedPatternComponents(`class`, constructor.effectiveFirstParameterSection)(
    CaseClassPatternComponents.presentableParameterText
  )

object CaseClassPatternComponents {
  private[clauses] def presentableParameterText(parameter: ScClassParameter): String =
    parameter.name + (if (parameter.isVarArgs) "@_*" else "")

  def unapply(`class`: ScConstructorOwner): Option[CaseClassPatternComponents] = for {
    constructor <- `class`.constructor
    if `class`.isCase
  } yield new CaseClassPatternComponents(`class`, constructor)
}

sealed abstract class PhysicalExtractorPatternComponents protected(`class`: ScTypeDefinition,
                                                                   types: Iterable[ScType])
  extends SequenceBasedPatternComponents(`class`, types)(new UniqueNameSuggester())

object PhysicalExtractorPatternComponents {

  def unapply(
    `class`: ScTypeDefinition
  )(implicit
    parameters: ClauseCompletionParameters
  ): Option[PhysicalExtractorPatternComponents] =
    for {
      Extractor(method) <- `class`.baseCompanion
      returnType        <- method.returnType.toOption
      types =
        ExtractorMatch.extractorMatches(returnType, parameters.place, method)
          .headOption
          .map(_.productTypes)
          .getOrElse(Seq.empty)
    } yield new PhysicalExtractorPatternComponents(`class`, types) {}
}

final class TuplePatternComponents(tupleClass: ScClass, types: Iterable[ScType])
  extends PhysicalExtractorPatternComponents(tupleClass, types) {

  override def presentablePatternText(reference: Either[String, ScStableCodeReference]): String =
    presentableSuffix
}

final class StablePatternComponents(`class`: PsiClass, qualifiedName: String)
  extends ClassPatternComponents(`class`, qualifiedName, ScalaTypePresentation.ObjectTypeSuffix) {

  def this(`object`: ScObject) = this(`object`, `object`.qualifiedName)
}

object WildcardPatternComponents extends PatternComponents {
  override def canonicalPatternText: String = Placeholder
}
