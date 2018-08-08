package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation

import scala.collection.JavaConverters

package object clauses {

  private[clauses] case class Inheritors(namedInheritors: Seq[ScTypeDefinition],
                                         anonymousInheritors: Seq[ScNewTemplateDefinition] = Seq.empty,
                                         javaInheritors: Seq[PsiClass] = Seq.empty) {

    def exhaustivePatterns: Seq[PatternComponents] =
      namedInheritors.map {
        case scalaObject: ScObject => new TypedPatternComponents(scalaObject, scalaObject.qualifiedName + ScalaTypePresentation.ObjectTypeSuffix)
        case ExtractorPatternComponents(components) => components
        case definition => new TypedPatternComponents(definition)
      } ++ javaInheritors.map {
        new TypedPatternComponents(_)
      } ++ (if (anonymousInheritors.nonEmpty) Some(WildcardPatternComponents) else None)

    def inexhaustivePatterns: Seq[ExtractorPatternComponents] =
      namedInheritors.collect {
        case ExtractorPatternComponents(components) => components
      }
  }

  private[clauses] object Inheritors {

    def apply(classes: Seq[PsiClass]): Inheritors = {
      val (scalaInheritors, javaInheritors) = classes.partition(_.isInstanceOf[ScTemplateDefinition])
      val (namedInheritors, anonymousInheritors) = scalaInheritors.partition(_.isInstanceOf[ScTypeDefinition])

      Inheritors(
        namedInheritors.map(_.asInstanceOf[ScTypeDefinition]),
        anonymousInheritors.map(_.asInstanceOf[ScNewTemplateDefinition]),
        javaInheritors
      )
    }
  }

  private[clauses] object SealedDefinition {

    def unapply(definition: ScTypeDefinition): Option[Seq[PsiClass]] = if (definition.isSealed) {
      import JavaConverters._
      val inheritors = DirectClassInheritorsSearch
        .search(definition, definition.resolveScope)
        .findAll()
        .asScala
        .toSeq
        .sortBy(_.getNavigationElement.getTextRange.getStartOffset)
      Some(inheritors)
    } else None
  }
}
