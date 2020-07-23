package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.{InsertHandler, JavaCompletionFeatures, JavaCompletionUtil}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.psi.{PsiClass, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

abstract class GlobalMembersFinder protected(protected val place: ScalaPsiElement,
                                             protected val accessAll: Boolean) {

  import GlobalMembersFinder._

  protected trait GlobalMemberInsertHandler {

    this: InsertHandler[LookupElement] =>

    def triggerGlobalMemberCompletionFeature(): Unit =
      FeatureUsageTracker
        .getInstance
        .triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)
  }

  protected final def isAccessible(member: PsiMember): Boolean =
    accessAll ||
      completion.isAccessible(member)(place)

  final def lookupItems(reference: ScReferenceExpression): Iterable[LookupElement] = {
    val nameAvailability = new NameAvailabilityPredicate(reference)
    candidates
      .map(_.createLookupItem(nameAvailability))
      .filterNot(_ == null)
  }

  protected def candidates: Iterable[GlobalMemberResult]

  protected abstract class GlobalMemberResult(protected val resolveResult: ScalaResolveResult,
                                              protected val classToImport: PsiClass,
                                              containingClass: Option[PsiClass] = None) {

    final def createLookupItem(nameAvailability: PsiNamedElement => NameAvailabilityState): LookupElement =
      if (isApplicable) {
        val lookupItem = resolveResult.createLookupElement(
          isClassName = true,
          containingClass = containingClass
        )

        buildItem(
          lookupItem,
          nameAvailability(lookupItem.getPsiElement)
        )
      } else {
        null
      }

    protected def buildItem(lookupItem: ScalaLookupItem,
                            state: NameAvailabilityState): LookupElement = {
      lookupItem.shouldImport = state != NameAvailabilityState.AVAILABLE
      lookupItem.setInsertHandler(createInsertHandler(state))
      lookupItem.withBooleanUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR)
    }

    protected def createInsertHandler(state: NameAvailabilityState): InsertHandler[LookupElement]

    private def isApplicable: Boolean = Option(classToImport.qualifiedName).forall(isNotExcluded)
  }
}

object GlobalMembersFinder {

  private final class NameAvailabilityPredicate(reference: ScReferenceExpression)
    extends (PsiNamedElement => NameAvailabilityState) {

    import NameAvailabilityState._

    private lazy val elements = reference
      .completionVariants()
      .toSet[ScalaLookupItem]
      .map(_.getPsiElement)

    override def apply(element: PsiNamedElement): NameAvailabilityState =
      if (elements.contains(element)) AVAILABLE
      else if (elements.exists(_.name == element.name)) CONFLICT
      else NO_CONFLICT
  }

  private def isNotExcluded(qualifiedName: String): Boolean = {
    CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.forall { excludedPackage =>
      qualifiedName != excludedPackage && !qualifiedName.startsWith(excludedPackage + ".")
    }
  }
}