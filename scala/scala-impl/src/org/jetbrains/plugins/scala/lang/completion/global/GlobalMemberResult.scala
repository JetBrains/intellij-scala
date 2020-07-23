package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.{InsertHandler, JavaCompletionUtil}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private abstract class GlobalMemberResult protected(protected val resolveResult: ScalaResolveResult,
                                                    protected val classToImport: PsiClass,
                                                    containingClass: Option[PsiClass] = None) {

  import GlobalMemberResult._

  protected def this(elementToImport: PsiNamedElement,
                     classToImport: PsiClass) = this(
    new ScalaResolveResult(elementToImport),
    classToImport,
    Some(classToImport)
  )

  final def createLookupItem(nameAvailability: PsiNamedElement => NameAvailabilityState): LookupElement =
    if (isApplicable) {
      val lookupItem = resolveResult.getLookupElement(
        isClassName = true,
        containingClass = containingClass
      ).get

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

  protected def createInsertHandler(state: NameAvailabilityState): InsertHandler[LookupElement] =
    createGlobalMemberInsertHandler(classToImport)

  private def isApplicable: Boolean = Option(classToImport.qualifiedName).forall(isNotExcluded)
}

private object GlobalMemberResult {

  private def isNotExcluded(qualifiedName: String): Boolean = {
    CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.forall { excludedPackage =>
      qualifiedName != excludedPackage && !qualifiedName.startsWith(excludedPackage + ".")
    }
  }
}
