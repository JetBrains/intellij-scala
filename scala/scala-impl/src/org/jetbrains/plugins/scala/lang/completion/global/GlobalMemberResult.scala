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
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private abstract class GlobalMemberResult protected(protected val resolveResult: ScalaResolveResult,
                                                    containingClass: Option[PsiClass])
                                                   (nameAvailability: NameAvailability) {
  private[global] def isApplicable: Boolean

  final def createLookupItem: LookupElement = {
    val lookupItem = resolveResult.createLookupElement(
      isClassName = true,
      containingClass = containingClass,
      shouldImport = nameAvailabilityState != NameAvailabilityState.AVAILABLE,
    )

    buildItem(lookupItem)
  }

  protected def buildItem(lookupItem: ScalaLookupItem): LookupElement = {
    lookupItem
      .setInsertHandler(createInsertHandler)
      .withBooleanUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR)
  }

  protected def createInsertHandler: InsertHandler[LookupElement]

  protected final def nameAvailabilityState: NameAvailabilityState =
    nameAvailability(resolveResult.getElement)
}

private abstract class GlobalPsiClassMemberResult protected(override protected val resolveResult: ScalaResolveResult,
                                                            protected val classToImport: PsiClass,
                                                            containingClass: Option[PsiClass] = None)
                                                           (nameAvailability: NameAvailability)
  extends GlobalMemberResult(resolveResult, containingClass)(nameAvailability) {

  import GlobalMemberResult._

  protected def this(elementToImport: PsiNamedElement,
                     classToImport: PsiClass)
                    (nameAvailability: NameAvailability) = this(
    new ScalaResolveResult(elementToImport),
    classToImport,
    Some(classToImport)
  )(nameAvailability)

  override private[global] def isApplicable: Boolean = Option(classToImport.qualifiedName).forall(isNotExcluded)

  override protected def createInsertHandler: InsertHandler[LookupElement] =
    createGlobalMemberInsertHandler(classToImport)
}

private abstract class GlobalTopLevelMemberResult protected(override protected val resolveResult: ScalaResolveResult,
                                                            protected val packageToImport: ScPackageLike)
                                                           (nameAvailability: NameAvailability)
  extends GlobalMemberResult(resolveResult, None)(nameAvailability) {

  override private[global] def isApplicable = Option(packageToImport.fqn).forall(GlobalMemberResult.isNotExcluded)

  override protected def createInsertHandler: InsertHandler[LookupElement] =
    createGlobalTopLevelMemberInsertHandler(packageToImport)
}

private object GlobalMemberResult {

  def isNotExcluded(qualifiedName: String): Boolean = {
    CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.forall { excludedPackage =>
      qualifiedName != excludedPackage && !qualifiedName.startsWith(excludedPackage + ".")
    }
  }
}
