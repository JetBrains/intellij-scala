package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.psi.PsiClass
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.autoImport.GlobalImplicitConversion
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ContainingClass}
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.implicits._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor

private[completion] final class ExtensionMethodsFinder(private val originalType: ScType,
                                                       override protected val place: ScExpression,
                                                       override protected val accessAll: Boolean)
  extends GlobalMembersFinder(place, accessAll)
    with CompanionObjectMembersFinder[ScFunction] {

  private val valueType = toValueType(originalType)

  override protected def candidates: Iterable[GlobalMemberResult] =
    super.candidates ++
      (if (accessAll) globalCandidates(new ApplicabilityPredicate) else Iterable.empty)

  private def globalCandidates(predicate: ScalaResolveResult => Boolean) = for {
    (GlobalImplicitConversion(owner: ScObject, _, function), application) <- ImplicitConversionData.getPossibleConversions(place)
    resolveResult <- candidatesForType(application.resultType)
    if predicate(resolveResult)
  } yield ExtensionMethodCandidate(resolveResult, owner, function)

  override protected def findTargets: Seq[PsiClass] = valueType match {
    case ExtractClass(ClassOrTrait(definition)) =>
      definition +: definition.supers
    case _ => Seq.empty
  }

  override protected def namedElementsIn(member: ScMember): Seq[ScFunction] = member match {
    case function: ScFunction =>
      function.parameters match {
        case Seq(head) if head.getRealParameterType.exists(valueType.conforms) =>
          Seq(function)
        case _ => Seq.empty
      }
    case _ => Seq.empty
  }

  override protected def createResult(resolveResult: ScalaResolveResult,
                                      classToImport: ScObject): GlobalMemberResult =
    ExtensionLikeCandidate(resolveResult, classToImport)

  private def candidatesForType(`type`: ScType) =
    CompletionProcessor.variants(`type`, place)

  private final case class ExtensionMethodCandidate(override val resolveResult: ScalaResolveResult,
                                                    override val classToImport: ScObject,
                                                    elementToImport: ScFunction)
    extends GlobalMemberResult(resolveResult, classToImport) {

    override protected def createInsertHandler(shouldImport: ThreeState): ScalaImportingInsertHandler = new ScalaImportingInsertHandler(classToImport) {

      override protected def qualifyAndImport(reference: ScReferenceExpression): Unit = for {
        ContainingClass(ClassQualifiedName(_)) <- Option(elementToImport.nameContext)
        holder = ScImportsHolder(reference)
      } holder.addImportForPsiNamedElement(
        elementToImport,
        null,
        Some(containingClass)
      )
    }
  }

  private final case class ExtensionLikeCandidate(override val resolveResult: ScalaResolveResult,
                                                  override val classToImport: ScObject)
    extends GlobalMemberResult(resolveResult, classToImport, Some(classToImport)) {

    override protected def createInsertHandler(shouldImport: ThreeState): InsertHandler[ScalaLookupItem] =
      (context: InsertionContext, _: ScalaLookupItem) => {
        val reference@ScReferenceExpression.withQualifier(qualifier) = context
          .getFile
          .findReferenceAt(context.getStartOffset)

        val function = resolveResult
          .getElement
          .asInstanceOf[ScFunction]
        val functionName = function.name

        val ScMethodCall(methodReference: ScReferenceExpression, _) =
          replaceReference(reference, functionName + "(" + qualifier.getText + ")")

        if (function != methodReference.resolve) {
          val ScReferenceExpression.withQualifier(objectReference: ScReferenceExpression) =
            replaceReference(methodReference, classToImport.name + "." + functionName)

          objectReference.bindToElement(classToImport)
        }
      }

    private def replaceReference(reference: ScReferenceExpression,
                                 text: String) =
      reference.replaceExpression(
        createExpressionWithContextFromText(text, reference.getContext, reference),
        removeParenthesis = true
      )
  }

  private class ApplicabilityPredicate extends (ScalaResolveResult => Boolean) {

    private lazy val originalTypeMemberNames = candidatesForType(originalType)
      .map(_.name)

    override def apply(resolveResult: ScalaResolveResult): Boolean =
      !originalTypeMemberNames.contains(resolveResult.name)
  }
}