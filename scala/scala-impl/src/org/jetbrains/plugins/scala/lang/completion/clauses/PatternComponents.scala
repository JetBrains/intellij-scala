package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypePresentation}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

private[clauses] sealed trait PatternComponents {
  def textFor(maybeTypeElement: Option[ScTypeElement] = None): String
}

private[clauses] class TypedPatternComponents protected(`class`: PsiClass,
                                                        qualifiedName: String,
                                                        suffix: String = "")
  extends PatternComponents {

  def this(`class`: ScTypeDefinition) = this(
    `class`,
    `class`.qualifiedName,
    `class`.typeParameters.length match {
      case 0 => ""
      case length => Seq.fill(length)(Placeholder).commaSeparated(Model.SquareBrackets)
    }
  )

  override def textFor(maybeTypeElement: Option[ScTypeElement]): String = {
    val maybeSuggestedName = maybeTypeElement.flatMap(_.`type`().toOption)
      .flatMap(NameSuggester.suggestNamesByType(_).headOption)

    maybeSuggestedName.getOrElse(extensions.Placeholder) +
      ": " +
      maybeTypeElement.fold(qualifiedName + suffix)(_.getText)
  }
}

private[clauses] sealed abstract class ExtractorPatternComponents[T](`class`: ScTypeDefinition,
                                                                     components: Seq[T])
  extends TypedPatternComponents(`class`) {

  final def extractorText(referenceText: String = `class`.name): String =
    referenceText + components
      .map(componentText)
      .commaSeparated(Model.Parentheses)

  override final def textFor(maybeTypeElement: Option[ScTypeElement] = None): String = maybeTypeElement match {
    case Some(ScSimpleTypeElement.unwrapped(ElementText(reference))) => extractorText(reference)
    case _ => super.textFor(maybeTypeElement)
  }

  protected def componentText(component: T): String
}

private[clauses] final class SyntheticExtractorPatternComponents private(`class`: ScClass,
                                                                         method: ScPrimaryConstructor)
  extends ExtractorPatternComponents(`class`, method.effectiveFirstParameterSection) {

  override protected def componentText(parameter: ScClassParameter): String =
    parameter.name + (if (parameter.isVarArgs) "@_*" else "")
}

private[clauses] object SyntheticExtractorPatternComponents {

  def unapply(`class`: ScClass): Option[SyntheticExtractorPatternComponents] =
    if (`class`.isCase)
      `class`.constructor.map(new SyntheticExtractorPatternComponents(`class`, _))
    else
      None
}

private[clauses] final class PhysicalExtractorPatternComponents private(`class`: ScTypeDefinition,
                                                                        components: Seq[ScType])
  extends ExtractorPatternComponents(`class`, components) {

  override protected def componentText(`type`: ScType): String = Placeholder
}

private[clauses] object PhysicalExtractorPatternComponents {

  def unapply(definition: ScTypeDefinition)
             (implicit place: PsiElement): Option[PhysicalExtractorPatternComponents] =
    for {
      Extractor(method) <- definition.baseCompanionModule
      returnType <- method.returnType.toOption
      components = ScPattern.extractorParameters(returnType, place, isOneArgCaseClass = false)
    } yield new PhysicalExtractorPatternComponents(definition, components)
}

private[clauses] final class StablePatternComponents(`class`: PsiClass, qualifiedName: String)
  extends TypedPatternComponents(`class`, qualifiedName, ScalaTypePresentation.ObjectTypeSuffix) {

  def this(`object`: ScObject) = this(`object`, `object`.qualifiedName)
}

private[clauses] object WildcardPatternComponents extends PatternComponents {
  override def textFor(maybeTypeElement: Option[ScTypeElement]): String = Placeholder
}