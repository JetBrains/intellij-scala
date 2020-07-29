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
                                                    containingClass: Option[PsiClass] = None)
                                                   (nameAvailability: NameAvailability) {

  import GlobalMemberResult._

  protected def this(elementToImport: PsiNamedElement,
                     classToImport: PsiClass)
                    (nameAvailability: NameAvailability) = this(
    new ScalaResolveResult(elementToImport),
    classToImport,
    Some(classToImport)
  )(nameAvailability)

  private[global] def isApplicable: Boolean = Option(classToImport.qualifiedName).forall(isNotExcluded)

  final def createLookupItem: LookupElement = {
    val lookupItem = resolveResult.getLookupElement(
      isClassName = true,
      containingClass = containingClass,
      shouldImport = nameAvailabilityState != NameAvailabilityState.AVAILABLE,
    ).get

    buildItem(lookupItem)
  }

  protected def buildItem(lookupItem: ScalaLookupItem): LookupElement = {
    lookupItem
      .setInsertHandler(createInsertHandler)
      .withBooleanUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR)
  }

  protected def createInsertHandler: InsertHandler[LookupElement] =
    createGlobalMemberInsertHandler(classToImport)

  protected final def nameAvailabilityState: NameAvailabilityState =
    nameAvailability(resolveResult.getElement)
}

private object GlobalMemberResult {

  private def isNotExcluded(qualifiedName: String): Boolean = {
    CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.forall { excludedPackage =>
      qualifiedName != excludedPackage && !qualifiedName.startsWith(excludedPackage + ".")
    }
  }
}
