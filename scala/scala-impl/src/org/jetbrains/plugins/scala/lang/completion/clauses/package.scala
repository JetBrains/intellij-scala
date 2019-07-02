package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementRenderer}
import com.intellij.patterns.{PlatformPatterns, PsiElementPattern}
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

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
                                         (presentation: LookupElementRenderer[LookupElement]): LookupElement =
    LookupElementBuilder.create(lookupString)
      .withInsertHandler(insertHandler)
      .withRenderer(presentation)

  private[clauses] case class Inheritors(namedInheritors: List[ScTypeDefinition],
                                         isInstantiatiable: Boolean = false)

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
        namedInheritors.asInstanceOf[List[ScTypeDefinition]],
        isInstantiatiable = isConcreteClass || anonymousInheritors.nonEmpty
      ))
    } else None

    private def directInheritors(definition: ScTypeDefinition) = {
      import JavaConverters._
      DirectClassInheritorsSearch
        .search(definition, definition.getContainingFile.getResolveScope)
        .findAll()
        .asScala
        .toIndexedSeq
        .sortBy(_.getNavigationElement.getTextRange.getStartOffset)
        .toList
    }
  }

  private[clauses] object Extractor {

    def unapply(`object`: ScObject): Option[ScFunctionDefinition] = `object`.membersWithSynthetic.collectFirst {
      case function: ScFunctionDefinition if function.isUnapplyMethod => function
    }
  }

}
