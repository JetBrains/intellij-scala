package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypePresentation}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.{UniqueNameSuggester, suggestNamesByType}

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
      s"${ScalaKeyword.CASE} $patternText ${psi.ScalaPsiUtil.functionArrow}"
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
        case parent => throw new IllegalArgumentException(s"Simple type expected, actual `${parent.getClass}`: ${parent.getText}")
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

final class CaseClassPatternComponents private(`class`: ScClass,
                                               constructor: ScPrimaryConstructor)
  extends SequenceBasedPatternComponents(`class`, constructor.effectiveFirstParameterSection)({ parameter =>
    parameter.name + (if (parameter.isVarArgs) "@_*" else "")
  })

object CaseClassPatternComponents {

  def unapply(`class`: ScClass): Option[CaseClassPatternComponents] = for {
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
      Extractor(method) <- `class`.baseCompanionModule
      returnType        <- method.returnType.toOption
      types = ScPattern.unapplySubpatternTypes(
        returnType,
        parameters.place,
        method
      )
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