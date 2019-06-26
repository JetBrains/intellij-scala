package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation}
import com.intellij.patterns.{PlatformPatterns, PsiElementPattern}
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation

import scala.collection.JavaConverters
import scala.reflect.{ClassTag, classTag}

package object clauses {

  private[clauses] def inside[
    Capture <: ScalaPsiElement : ClassTag
  ]: PsiElementPattern.Capture[PsiElement] =
    PlatformPatterns.psiElement.inside {
      classTag[Capture].runtimeClass.asInstanceOf[Class[Capture]]
    }

  private[clauses] def buildLookupElement(lookupString: String,
                                          insertHandler: ClauseInsertHandler[_])
                                         (itemTextItalic: Boolean = false,
                                          itemTextBold: Boolean = false,
                                          tailText: String = null,
                                          grayed: Boolean = false): LookupElement =
    LookupElementBuilder.create {
      lookupString
    }.withInsertHandler {
      insertHandler
    }.withRenderer {
      (_: LookupElement, presentation: LookupElementPresentation) => {
        presentation.setItemText(lookupString)
        presentation.setItemTextItalic(itemTextItalic)
        presentation.setItemTextBold(itemTextBold)
        presentation.setTailText(tailText, grayed)
      }
    }

  private[clauses] case class Inheritors(namedInheritors: Seq[ScTypeDefinition],
                                         isInstantiatiable: Boolean = false) {

    def exhaustivePatterns: Seq[PatternComponents] =
      namedInheritors.map {
        case scalaObject: ScObject => new TypedPatternComponents(scalaObject, scalaObject.qualifiedName + ScalaTypePresentation.ObjectTypeSuffix)
        case SyntheticExtractorPatternComponents(components) => components
        case definition => new TypedPatternComponents(definition)
      } ++ (if (isInstantiatiable) Some(WildcardPatternComponents) else None)

    def inexhaustivePatterns(implicit place: PsiElement): Seq[ExtractorPatternComponents[_]] =
      namedInheritors.collect {
        case SyntheticExtractorPatternComponents(components) => components
        case PhysicalExtractorPatternComponents(components) => components
      }
  }

  private[clauses] object SealedDefinition {

    def unapply(definition: ScTypeDefinition): Option[Inheritors] = if (definition.isSealed) {
      val (namedInheritors, anonymousInheritors) = directInheritors(definition).partition {
        _.isInstanceOf[ScTypeDefinition]
      }

      val isConcreteClass = definition match {
        case scalaClass: ScClass => !scalaClass.hasAbstractModifier
        case _ => false
      }

      Some(Inheritors(
        namedInheritors.asInstanceOf[Seq[ScTypeDefinition]],
        isInstantiatiable = isConcreteClass || anonymousInheritors.nonEmpty
      ))
    } else None

    private def directInheritors(definition: ScTypeDefinition) = {
      import JavaConverters._
      DirectClassInheritorsSearch
        .search(definition, definition.getContainingFile.getResolveScope)
        .findAll()
        .asScala
        .toSeq
        .sortBy(_.getNavigationElement.getTextRange.getStartOffset)
    }
  }

  private[clauses] object Extractor {

    def unapply(`object`: ScObject): Option[ScFunctionDefinition] = `object`.membersWithSynthetic.collectFirst {
      case function: ScFunctionDefinition if function.isUnapplyMethod => function
    }
  }

}
