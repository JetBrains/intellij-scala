package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementRenderer}
import com.intellij.openapi.project.Project
import com.intellij.patterns.{PlatformPatterns, PsiElementPattern}
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.search.{GlobalSearchScope, ProjectScope}
import com.intellij.psi.{PsiAnonymousClass, PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
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

  private[clauses] case class Inheritors(namedInheritors: List[PsiClass],
                                         isExhaustive: Boolean) {

    if (namedInheritors.isEmpty) throw new IllegalArgumentException("Class contract violation")
  }

  private[clauses] object DirectInheritors {

    def unapply(`class`: ScTypeDefinition): Option[Inheritors] = {
      val isSealed = `class`.isSealed
      val scope = if (isSealed) `class`.getContainingFile.getResolveScope
      else projectScope(`class`.getProject)

      val (namedInheritors, anonymousInheritors) = directInheritors(`class`, scope).partition {
        case _: ScNewTemplateDefinition |
             _: PsiAnonymousClass => false
        case _ => true
      }

      implicit val ordered: Ordering[PsiClass] =
        if (isSealed) Ordering.by(_.getNavigationElement.getTextRange.getStartOffset)
        else Ordering.by(_.getName)

      namedInheritors.sorted.toList match {
        case Nil => None
        case inheritors =>
          val isNotConcrete = `class` match {
            case scalaClass: ScClass => scalaClass.hasAbstractModifier
            case _ => true
          }

          val isExhaustive = isSealed && isNotConcrete && anonymousInheritors.isEmpty
          Some(Inheritors(inheritors, isExhaustive))
      }
    }

    private def directInheritors(`class`: ScTypeDefinition,
                                 scope: GlobalSearchScope) = {
      import collection.JavaConverters._
      DirectClassInheritorsSearch
        .search(`class`, scope)
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

  private[this] def projectScope(implicit project: Project): GlobalSearchScope = {
    import ProjectScope._
    if (applicationUnitTestModeEnabled) getEverythingScope(project)
    else getProjectScope(project)
  }
}
