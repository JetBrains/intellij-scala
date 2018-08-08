package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.clauses.WildcardPatternComponents.{toString => Placeholder}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation

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

private[clauses] class ExtractorPatternComponents private(clazz: ScClass, constructor: ScPrimaryConstructor)
  extends TypedPatternComponents(clazz) {

  def extractorText(referenceText: String = clazz.name)
                   (parameterText: ScClassParameter => String = Function.const(Placeholder)): String =
    referenceText + constructor.effectiveFirstParameterSection
      .map(parameterText)
      .commaSeparated(Model.Parentheses)
}

private[clauses] object ExtractorPatternComponents {

  def unapply(scalaClass: ScClass): Option[ExtractorPatternComponents] =
    (if (scalaClass.isCase) scalaClass.constructor else None).map {
      new ExtractorPatternComponents(scalaClass, _)
    }
}

private[clauses] class StablePatternComponents(clazz: PsiClass,
                                               qualifiedName: String,
                                               name: String)
  extends TypedPatternComponents(clazz, qualifiedName) {

  override def toString: String = s"${super.toString}.$name${ScalaTypePresentation.ObjectTypeSuffix}"
}

private[clauses] object WildcardPatternComponents
  extends PatternComponents {

  override val toString: String = "_"
}