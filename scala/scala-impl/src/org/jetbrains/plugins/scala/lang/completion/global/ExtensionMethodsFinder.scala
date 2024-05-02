package org.jetbrains.plugins.scala.lang.completion.global

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.autoImport.{GlobalMember, GlobalMemberOwner}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
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
    if (accessAll) {
      val candidates = extensionMethodCandidates
      val candidatesScala3 = scala3ExtensionMethodCandidates
      val result = candidates ++ candidatesScala3
      result
    }
    else Iterable.empty

  private def collectExtensionMethodCandidates[M <: GlobalMember[ScFunction], A](possibleMembers: Map[M, A])
                                                                                (getResolveResults: (A, PsiNamedElement) => Set[ScalaResolveResult]) =
    for {
      (globalMember, application) <- possibleMembers
      candidateConstructor <- Iterable.from(extensionMethodCandidateConstructor(globalMember.owner))
      elementToImport = globalMember.member
      resolveResult <- getResolveResults(application, elementToImport)
    } yield candidateConstructor(resolveResult, elementToImport)

  private def extensionMethodCandidates =
    collectExtensionMethodCandidates(ImplicitConversionData.getPossibleConversions(place)) { (application, _) =>
      candidatesForType(application.resultType)
    }

  private def scala3ExtensionMethodCandidates =
    collectExtensionMethodCandidates(ExtensionMethodData.getPossibleExtensionMethods(place)) {
      case (_, elementToImport: ScFunction) =>
        val resolveResult = new ScalaResolveResult(
          elementToImport,
          isExtensionCall = elementToImport.isExtensionMethod,
          extensionContext = elementToImport.extensionMethodOwner
        )
        Set(resolveResult)
      case (_, elementToImport) => Set(new ScalaResolveResult(elementToImport))
    }

  private def extensionMethodCandidateConstructor(owner: GlobalMemberOwner): Option[(ScalaResolveResult, PsiNamedElement) => GlobalMemberResult] =
    Option(owner).flatMap {
      case GlobalMemberOwner(classToImport: ScObject) =>
        Some((resolveRes, elementToImport) => ExtensionMethodCandidate(resolveRes, classToImport, elementToImport))
      case GlobalMemberOwner(packageToImport: ScPackageLike) =>
        Some((resolveRes, elementToImport) => TopLevelExtensionMethodCandidate(resolveRes, packageToImport, elementToImport))
      case GlobalMemberOwner.GivenDefinition(definitionToImport) =>
        definitionToImport.containingClass match {
          case classToImport: ScObject =>
            Some((resolveRes, _) => ExtensionMethodCandidate(resolveRes, classToImport, definitionToImport))
          case null =>
            definitionToImport.getContext.asOptionOf[ScPackaging].map { packageToImport =>
              (resolveRes, _) => TopLevelExtensionMethodCandidate(resolveRes, packageToImport, definitionToImport)
            }
          case _ => None
        }
      case _ => None
    }

  private def candidatesForType(`type`: ScType) =
    CompletionProcessor.variants(`type`, place)

  import NameAvailabilityState._

  private object NameAvailability extends NameAvailability {

    private lazy val originalTypeMemberNames = candidatesForType(originalType)
      .map(_.name)

    override def apply(element: PsiNamedElement): NameAvailabilityState =
      if (originalTypeMemberNames.contains(element.name)) CONFLICT
      else NO_CONFLICT
  }

  @nowarn("msg=The outer reference in this type test cannot be checked at run time")
  private final case class ExtensionMethodCandidate(override val resolveResult: ScalaResolveResult,
                                                    override val classToImport: ScObject,
                                                    elementToImport: PsiNamedElement)
    extends GlobalPsiClassMemberResult(resolveResult, classToImport)(NameAvailability) {

    override private[global] def isApplicable =
      super.isApplicable &&
        nameAvailabilityState == NO_CONFLICT

    override protected def createInsertHandler: InsertHandler[LookupElement] =
      createGlobalMemberInsertHandler(elementToImport, classToImport)
  }

  private case class TopLevelExtensionMethodCandidate(override val resolveResult: ScalaResolveResult,
                                                      override val packageToImport: ScPackageLike,
                                                      elementToImport: PsiNamedElement)
    extends GlobalTopLevelMemberResult(resolveResult, packageToImport)(NameAvailability) {

    override private[global] def isApplicable =
      super.isApplicable &&
        nameAvailabilityState == NO_CONFLICT

    override protected def createInsertHandler: InsertHandler[LookupElement] =
      createGlobalTopLevelMemberInsertHandler(elementToImport, packageToImport)
  }
}