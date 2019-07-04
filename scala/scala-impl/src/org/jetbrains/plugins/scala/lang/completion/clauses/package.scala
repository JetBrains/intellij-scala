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

package object clauses {

  private[clauses] def inside[
    Capture <: ScalaPsiElement : reflect.ClassTag
  ]: PsiElementPattern.Capture[PsiElement] =
    PlatformPatterns.psiElement.inside {
      reflect.classTag[Capture].runtimeClass.asInstanceOf[Class[Capture]]
    }

  private[clauses] def buildLookupElement(lookupString: String,
                                          insertHandler: ClauseInsertHandler[_])
                                         (presentation: LookupElementRenderer[LookupElement]): LookupElement =
    LookupElementBuilder.create(lookupString)
      .withInsertHandler(insertHandler)
      .withRenderer(presentation)

  private[clauses] case class Inheritors(namedInheritors: List[ScTypeDefinition],
                                         isExhaustive: Boolean) {

    if (namedInheritors.isEmpty) throw new IllegalArgumentException("Class contract violation")
  }

  private[clauses] object DirectInheritors {

    def unapply(definition: ScTypeDefinition): Option[Inheritors] =
      directInheritors(definition).partition(_.isInstanceOf[ScTypeDefinition]) match {
        case (Seq(), _) => None
        case (namedInheritors, anonymousInheritors) =>
          val isSealed = definition.isSealed
          val inheritors = if (isSealed)
            namedInheritors.sortBy(_.getNavigationElement.getTextRange.getStartOffset)
          else
            namedInheritors

          val isNotConcrete = definition match {
            case scalaClass: ScClass => scalaClass.hasAbstractModifier
            case _ => true
          }

          Some(Inheritors(
            inheritors.toList.asInstanceOf[List[ScTypeDefinition]],
            isSealed && isNotConcrete && anonymousInheritors.isEmpty
          ))
      }

    private def directInheritors(definition: ScTypeDefinition) = {
      import collection.JavaConverters._
      DirectClassInheritorsSearch
        .search(definition)
        .findAll()
        .asScala
        .toIndexedSeq
    }
  }

  private[clauses] object Extractor {

    def unapply(`object`: ScObject): Option[ScFunctionDefinition] = `object`.membersWithSynthetic.collectFirst {
      case function: ScFunctionDefinition if function.isUnapplyMethod => function
    }
  }

}
