package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation, LookupElementRenderer}
import org.jetbrains.plugins.scala.autoImport.GlobalImplicitConversion
import org.jetbrains.plugins.scala.extensions.TraversableExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionData
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor

private[completion] final class ExtensionMethodsFinder(private val originalType: ScType,
                                                       override protected val place: ScExpression,
                                                       override protected val accessAll: Boolean)
  extends GlobalMembersFinder(place, accessAll) {

  import ExtensionMethodsFinder._

  private val valueType = toValueType(originalType)

  override protected[global] def candidates: Iterable[GlobalMemberResult] =
    localCandidates ++
      (if (accessAll) globalCandidates(ApplicabilityPredicate) else Iterable.empty)

  private def globalCandidates(predicate: ScalaResolveResult => Boolean) = for {
    (GlobalImplicitConversion(classToImport: ScObject, _, elementToImport), application) <- ImplicitConversionData.getPossibleConversions(place)
    resolveResult <- candidatesForType(application.resultType)
    if predicate(resolveResult)
  } yield ExtensionMethodCandidate(resolveResult, classToImport, elementToImport)

  private def targetTypeDefinitions: Seq[ScTypeDefinition] = valueType match {
    case ExtractClass(definition: ScTypeDefinition) =>
      (definition +: definition.supers.filterByType[ScTypeDefinition]) ++
        contextsOfType[ScTypeDefinition]
    case _ => Seq.empty
  }

  private def localCandidates = objectCandidates(targetTypeDefinitions) {
    case function: ScFunction =>
      function.parameters match {
        case Seq(head) if head.getRealParameterType.exists(valueType.conforms) =>
          Seq(function)
        case _ => Seq.empty
      }
    case _ => Seq.empty
  }(ExtensionLikeCandidate)

  private def candidatesForType(`type`: ScType) =
    CompletionProcessor.variants(`type`, place)

  private object ApplicabilityPredicate extends (ScalaResolveResult => Boolean) {

    private lazy val originalTypeMemberNames = candidatesForType(originalType)
      .map(_.name)

    override def apply(resolveResult: ScalaResolveResult): Boolean =
      !originalTypeMemberNames.contains(resolveResult.name)
  }

}

private object ExtensionMethodsFinder {

  final case class ExtensionMethodCandidate(override val resolveResult: ScalaResolveResult,
                                            override val classToImport: ScObject,
                                            elementToImport: ScFunction)
    extends GlobalMemberResult(resolveResult, classToImport) {

    override protected def createInsertHandler(state: NameAvailabilityState): InsertHandler[LookupElement] =
      createGlobalMemberInsertHandler(elementToImport, classToImport)
  }

  final case class ExtensionLikeCandidate(elementToImport: ScFunction,
                                          override val classToImport: ScObject)
    extends GlobalMemberResult(elementToImport, classToImport) {

    override protected def buildItem(lookupItem: ScalaLookupItem,
                                     state: NameAvailabilityState): LookupElement =
      LookupElementBuilder
        .create(elementToImport)
        .withInsertHandler(createInsertHandler(state))
        .withRenderer(createRenderer(lookupItem))

    override protected def createInsertHandler(state: NameAvailabilityState): InsertHandler[LookupElement] =
      (context: InsertionContext, _: LookupElement) => {
        val reference@ScReferenceExpression.withQualifier(qualifier) = context
          .getFile
          .findReferenceAt(context.getStartOffset)

        val replacement = createExpressionWithContextFromText(
          elementToImport.name + "(" + qualifier.getText + ")",
          reference.getContext,
          reference
        )

        val ScMethodCall(methodReference: ScReferenceExpression, _) = reference.replaceExpression(
          replacement,
          removeParenthesis = true
        )

        methodReference.bindToElement(
          elementToImport,
          Some(classToImport)
        )
      }

    private def createRenderer(lookupItem: ScalaLookupItem): LookupElementRenderer[LookupElement] =
      (_: LookupElement, presentation: LookupElementPresentation) => {
        val delegate = new LookupElementPresentation
        lookupItem.renderElement(delegate)
        presentation.copyFrom(delegate)

        // todo could be improved, requires ScalaLookupItem.tailText being decomposed
        //  see com.intellij.codeInsight.lookup.LookupElementPresentation#getTailFragments
        val newTailText = presentation.getTailText match {
          case null => null
          case tailText =>
            // contains at least one space character
            tailText.substring(tailText.lastIndexOf(' '))
        }
        presentation.setTailText(newTailText)
      }
  }

}