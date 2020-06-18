package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.{JavaCompletionFeatures, JavaCompletionUtil}
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.psi.{PsiClass, PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

abstract class GlobalMembersFinder {

  import GlobalMembersFinder._

  FeatureUsageTracker.getInstance.triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)

  final def lookupItems(reference: ScReferenceExpression, originalFile: PsiFile): Seq[ScalaLookupItem] = {
    val shouldImport = new ShouldImportPredicate(reference, originalFile)

    candidates.flatMap(_.createLookupItem(shouldImport)).toSeq
  }

  protected def candidates: Iterable[GlobalMemberResult]

  protected abstract class GlobalMemberResult(resolveResult: ScalaResolveResult,
                                              classToImport: PsiClass,
                                              containingClass: Option[PsiClass] = None) {

    final def createLookupItem(shouldImport: PsiNamedElement => Boolean): Option[ScalaLookupItem] =
      if (isApplicable)
        resolveResult.getLookupElement(
          isClassName = true,
          containingClass = containingClass
        ).map { lookupItem =>
          lookupItem.shouldImport = shouldImport(lookupItem.getPsiElement)
          patchItem(lookupItem)
          lookupItem.withBooleanUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR)
        }
      else
        None

    protected def patchItem(lookupItem: ScalaLookupItem): Unit = {
    }

    private def isApplicable: Boolean = Option(classToImport.qualifiedName).forall(isNotExcluded)
  }
}

object GlobalMembersFinder {

  private final class ShouldImportPredicate(reference: ScReferenceExpression,
                                            originalFile: PsiFile) extends (PsiNamedElement => Boolean) {

    private lazy val elements = reference
      .completionVariants()
      .toSet[ScalaLookupItem]
      .map(_.getPsiElement)

    override def apply(element: PsiNamedElement): Boolean = element.getContainingFile match {
      case `originalFile` =>
        contextContainingClassName(element).forall { className =>
          //complex logic to detect static methods in the same file, which we shouldn't import
          val name = element.name
          !elements
            .filter(_.getContainingFile == originalFile)
            .filter(_.name == name)
            .flatMap(contextContainingClassName)
            .contains(className)
        }
      case _ => !elements.contains(element)
    }

    private def contextContainingClassName(element: PsiNamedElement) =
      element.containingClassOfNameContext.flatMap { clazz =>
        Option(clazz.qualifiedName)
      }
  }

  private def isNotExcluded(qualifiedName: String): Boolean = {
    CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.forall { excludedPackage =>
      qualifiedName != excludedPackage && !qualifiedName.startsWith(excludedPackage + ".")
    }
  }
}