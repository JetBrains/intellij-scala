package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.autoImport.{GlobalExtensionMethod, GlobalImplicitConversion}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.implicits.{ExtensionMethodData, ImplicitConversionData}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor

import scala.annotation.nowarn

private final class ExtensionMethodsFinder(originalType: ScType,
                                           place: ScExpression,
                                           accessAll: Boolean)
  extends ByTypeGlobalMembersFinder(originalType, place, accessAll) {

  override protected[global] def candidates: Iterable[GlobalMemberResult] =
    if (accessAll) extensionMethodCandidates ++ scala3ExtensionMethodCandidates else Iterable.empty

  private def extensionMethodCandidates = for {
    (GlobalImplicitConversion(classToImport: ScObject, _, elementToImport), application) <- ImplicitConversionData.getPossibleConversions(place)
    resolveResult <- candidatesForType(application.resultType)
  } yield ExtensionMethodCandidate(resolveResult, classToImport, elementToImport)

  private def scala3ExtensionMethodCandidates = for {
    (GlobalExtensionMethod(classToImport: ScObject, _, elementToImport), _) <- ExtensionMethodData.getPossibleExtensionMethods(place)
    resolveResult = new ScalaResolveResult(elementToImport, isExtension = elementToImport.isExtensionMethod, extensionContext = elementToImport.extensionMethodOwner)
  } yield ExtensionMethodCandidate(resolveResult, classToImport, elementToImport)

  private def candidatesForType(`type`: ScType) =
    CompletionProcessor.variants(`type`, place)

  import NameAvailabilityState._

  private object NameAvailability extends global.NameAvailability {

    private lazy val originalTypeMemberNames = candidatesForType(originalType)
      .map(_.name)

    override def apply(element: PsiNamedElement): NameAvailabilityState =
      if (originalTypeMemberNames.contains(element.name)) CONFLICT
      else NO_CONFLICT
  }

  @nowarn("msg=The outer reference in this type test cannot be checked at run time")
  private final case class ExtensionMethodCandidate(override val resolveResult: ScalaResolveResult,
                                                    override val classToImport: ScObject,
                                                    elementToImport: ScFunction)
    extends GlobalMemberResult(resolveResult, classToImport)(NameAvailability) {

    override private[global] def isApplicable =
      super.isApplicable &&
        nameAvailabilityState == NO_CONFLICT

    override protected def createInsertHandler: InsertHandler[LookupElement] =
      createGlobalMemberInsertHandler(elementToImport, classToImport)
  }
}