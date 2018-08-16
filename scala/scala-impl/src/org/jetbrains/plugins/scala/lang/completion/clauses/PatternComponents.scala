package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypePresentation}

private[clauses] sealed trait PatternComponents

private[clauses] class TypedPatternComponents(clazz: PsiClass,
                                              qualifiedName: String,
                                              length: Int = 0)
  extends PatternComponents {

  //noinspection ScalaWrongMethodsUsage
  def this(clazz: PsiClass) =
    this(clazz, clazz.getQualifiedName, clazz.getTypeParameters.length)

  def this(clazz: ScTypeDefinition) =
    this(clazz, clazz.qualifiedName, clazz.typeParameters.length)

  override def toString: String = {
    val suffix = length match {
      case 0 => ""
      case _ => Seq.fill(length)(Placeholder).commaSeparated(Model.SquareBrackets)
    }
    s"$Placeholder: $qualifiedName$suffix"
  }
}

private[clauses] sealed abstract class ExtractorPatternComponents[T](clazz: ScTypeDefinition,
                                                                     components: Seq[T])
  extends TypedPatternComponents(clazz) {

  final def defaultExtractorText(referenceText: String = clazz.name): String =
    extractorText(referenceText) {
      Function.const(Placeholder)
    }

  final def extractorText(referenceText: String)
                         (componentText: T => String): String =
    referenceText + components
      .map(componentText)
      .commaSeparated(Model.Parentheses)
}

private[clauses] class SyntheticExtractorPatternComponents private(clazz: ScClass,
                                                                   method: ScPrimaryConstructor)
  extends ExtractorPatternComponents(clazz, method.effectiveFirstParameterSection)

private[clauses] object SyntheticExtractorPatternComponents {

  def unapply(scalaClass: ScClass): Option[SyntheticExtractorPatternComponents] =
    (if (scalaClass.isCase) scalaClass.constructor else None).map {
      new SyntheticExtractorPatternComponents(scalaClass, _)
    }
}

private[clauses] class PhysicalExtractorPatternComponents private(clazz: ScTypeDefinition,
                                                                  components: Seq[ScType])
  extends ExtractorPatternComponents(clazz, components)

private[clauses] object PhysicalExtractorPatternComponents {

  def unapply(definition: ScTypeDefinition)
             (implicit place: PsiElement): Option[PhysicalExtractorPatternComponents] =
    for {
      Extractor(method) <- definition.baseCompanionModule
      returnType <- method.returnType.toOption
      components = ScPattern.extractorParameters(returnType, place, isOneArgCaseClass = false)
    } yield new PhysicalExtractorPatternComponents(definition, components)
}

private[clauses] class StablePatternComponents(clazz: PsiClass,
                                               qualifiedName: String,
                                               name: String)
  extends TypedPatternComponents(clazz, qualifiedName) {

  override def toString: String = s"${super.toString}.$name${ScalaTypePresentation.ObjectTypeSuffix}"
}

private[clauses] object WildcardPatternComponents
  extends PatternComponents {

  override val toString: String = Placeholder
}