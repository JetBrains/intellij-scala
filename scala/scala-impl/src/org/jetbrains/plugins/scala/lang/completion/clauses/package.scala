package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation

import scala.collection.JavaConverters

package object clauses {

  private[clauses] sealed trait PatternComponents

  private[clauses] class TypedPatternComponents(private val clazz: PsiClass,
                                                qualifiedName: String,
                                                length: Int = 0)
    extends PatternComponents {

    override def toString: String = {
      val suffix = length match {
        case 0 => ""
        case _ => Seq.fill(length)(WildcardPatternComponents.toString).commaSeparated(model = Model.SquareBrackets)
      }
      s"$WildcardPatternComponents: $qualifiedName$suffix"
    }
  }

  private[clauses] object TypedPatternComponents {

    def unapply(components: TypedPatternComponents): Option[ScPrimaryConstructor] =
      components.clazz match {
        case caseClass: ScClass if caseClass.isCase => caseClass.constructor
        case _ => None
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

  private[clauses] case class Inheritors(namedInheritors: Seq[ScTypeDefinition],
                                         anonymousInheritors: Seq[ScNewTemplateDefinition] = Seq.empty,
                                         javaInheritors: Seq[PsiClass] = Seq.empty) {

    import Inheritors._

    def exhaustivePatterns: Seq[PatternComponents] =
      namedInheritors.map(components) ++
        javaInheritors.map(components) ++
        (if (anonymousInheritors.nonEmpty) Some(WildcardPatternComponents) else None)
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

    private def components(definition: ScTypeDefinition) = {
      val suffix = definition match {
        case _: ScObject => ScalaTypePresentation.ObjectTypeSuffix
        case _ => ""
      }
      new TypedPatternComponents(definition, definition.qualifiedName + suffix, definition.typeParameters.length)
    }

    private def components(clazz: PsiClass) =
    //noinspection ScalaWrongMethodsUsage
      new TypedPatternComponents(clazz, clazz.getQualifiedName, clazz.getTypeParameters.length)
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
