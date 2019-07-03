package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScTypeParam}
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

    def canonicalClauseText(implicit place: PsiElement): String =
      clauseText(components.canonicalPatternText)

    def clauseText(patternText: String)
                  (implicit place: PsiElement): String =
      s"${ScalaKeyword.CASE} $patternText ${psi.ScalaPsiUtil.functionArrow}"
  }
}

sealed abstract class ClassPatternComponents[T](`class`: PsiClass,
                                                qualifiedName: String,
                                                components: Seq[T])
  extends PatternComponents {

  protected val suffix: String = components
    .map(componentText)
    .commaSeparated(model(components))

  def this(`class`: ScTypeDefinition, components: Seq[T]) = this(
    `class`,
    `class`.qualifiedName,
    components
  )

  override def canonicalPatternText: String =
    namedPatternText(Left(qualifiedName))

  def presentablePatternText(reference: Either[String, ScStableCodeReference] = Left(`class`.name)): String =
    reference match {
      case Right(reference) => reference.getText
      case Left(text) => text
      case _ => presentablePatternText()
    }

  protected def componentText(component: T): String = Placeholder

  protected def model(components: Seq[T]): Model.Val = Model.Parentheses

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

    suggestedName + ": " + reference.fold(identity, _.getText)
  }
}

final class TypedPatternComponents(`class`: ScTypeDefinition)
  extends ClassPatternComponents(`class`, `class`.typeParameters) {

  override def canonicalPatternText: String =
    super.canonicalPatternText + suffix

  override def presentablePatternText(reference: Either[String, ScStableCodeReference]): String =
    namedPatternText(reference) + suffix

  override protected def model(components: Seq[ScTypeParam]): Model.Val =
    if (components.isEmpty) Model.None
    else Model.SquareBrackets
}

final class CaseClassPatternComponents private(`class`: ScClass,
                                               constructor: ScPrimaryConstructor)
  extends ClassPatternComponents(`class`, constructor.effectiveFirstParameterSection) {

  override def presentablePatternText(reference: Either[String, ScStableCodeReference]): String =
    super.presentablePatternText(reference) + suffix

  override protected def componentText(parameter: ScClassParameter): String =
    parameter.name + (if (parameter.isVarArgs) "@_*" else "")
}

object CaseClassPatternComponents {

  def unapply(`class`: ScClass): Option[CaseClassPatternComponents] =
    if (`class`.isCase)
      `class`.constructor.map(new CaseClassPatternComponents(`class`, _))
    else
      None
}

final class TuplePatternComponents(tupleClass: ScClass, types: Seq[ScType]) extends {
  private val suggester = new UniqueNameSuggester()
} with ClassPatternComponents(tupleClass, types) {

  override def canonicalPatternText: String =
    super.canonicalPatternText +
      types.map(_.canonicalText).commaSeparated(Model.SquareBrackets)

  override def presentablePatternText(reference: Either[String, ScStableCodeReference]): String =
    suffix

  override protected def componentText(`type`: ScType): String =
    suggester(`type`)
}

final class PhysicalExtractorPatternComponents private(`class`: ScTypeDefinition,
                                                       components: Seq[ScType])
  extends ClassPatternComponents(`class`, components) {

  override def presentablePatternText(reference: Either[String, ScStableCodeReference]): String =
    super.presentablePatternText(reference) + suffix
}

object PhysicalExtractorPatternComponents {

  def unapply(definition: ScTypeDefinition)
             (implicit place: PsiElement): Option[PhysicalExtractorPatternComponents] =
    for {
      Extractor(method) <- definition.baseCompanionModule
      returnType <- method.returnType.toOption
      components = ScPattern.extractorParameters(returnType, place, isOneArgCaseClass = false)
    } yield new PhysicalExtractorPatternComponents(definition, components)
}

final class StablePatternComponents(`class`: PsiClass, qualifiedName: String) extends {
  override protected val suffix: String = ScalaTypePresentation.ObjectTypeSuffix
} with ClassPatternComponents(`class`, qualifiedName, Seq.empty) {

  def this(`object`: ScObject) = this(`object`, `object`.qualifiedName)

  override def canonicalPatternText: String =
    super.canonicalPatternText + suffix
}

object WildcardPatternComponents extends PatternComponents {
  override def canonicalPatternText: String = Placeholder
}