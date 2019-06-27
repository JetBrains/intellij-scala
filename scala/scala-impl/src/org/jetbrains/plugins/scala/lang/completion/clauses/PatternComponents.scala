package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypePresentation}

private[clauses] sealed trait PatternComponents {
  def text: String
}

private[clauses] class TypedPatternComponents protected(clazz: PsiClass,
                                                        qualifiedName: String,
                                                        suffix: String = "")
  extends PatternComponents {

  def this(clazz: ScTypeDefinition) = this(
    clazz,
    clazz.qualifiedName,
    clazz.typeParameters.length match {
      case 0 => ""
      case length => Seq.fill(length)(Placeholder).commaSeparated(Model.SquareBrackets)
    }
  )

  override final def text: String = s"$Placeholder: $qualifiedName$suffix"
}

private[clauses] final class ObjectPatternComponents(scalaObject: ScObject)

private[clauses] sealed abstract class ExtractorPatternComponents[T](clazz: ScTypeDefinition,
                                                                     components: Seq[T])
  extends TypedPatternComponents(clazz) {

  final def extractorText(referenceText: String = clazz.name): String =
    referenceText + components
      .map(componentText)
      .commaSeparated(Model.Parentheses)

  protected def componentText(component: T): String
}

private[clauses] final class SyntheticExtractorPatternComponents private(clazz: ScClass,
                                                                         method: ScPrimaryConstructor)
  extends ExtractorPatternComponents(clazz, method.effectiveFirstParameterSection) {

  override protected def componentText(parameter: ScClassParameter): String =
    parameter.name + (if (parameter.isVarArgs) "@_*" else "")
}

private[clauses] object SyntheticExtractorPatternComponents {

  def unapply(scalaClass: ScClass): Option[SyntheticExtractorPatternComponents] =
    (if (scalaClass.isCase) scalaClass.constructor else None).map {
      new SyntheticExtractorPatternComponents(scalaClass, _)
    }
}

private[clauses] final class PhysicalExtractorPatternComponents private(clazz: ScTypeDefinition,
                                                                        components: Seq[ScType])
  extends ExtractorPatternComponents(clazz, components) {

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

private[clauses] final class StablePatternComponents(clazz: PsiClass, qualifiedName: String)
  extends TypedPatternComponents(clazz, qualifiedName, ScalaTypePresentation.ObjectTypeSuffix) {

  def this(`object`: ScObject) = this(`object`, `object`.qualifiedName)
}

private[clauses] object WildcardPatternComponents
  extends PatternComponents {

  override def text: String = Placeholder
}