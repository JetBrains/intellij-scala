package org.jetbrains.plugins.scala.lang.completion.global

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementRenderer}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.toValueType
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass

private final class HoogleFinder(originalType: ScType,
                                 place: ScExpression,
                                 accessAll: Boolean)
  extends ByTypeGlobalMembersFinder(originalType, place, accessAll) {

  import HoogleFinder._

  private val valueType = toValueType(originalType)

  override protected[global] def candidates: Iterable[GlobalMemberResult] =
    objectCandidates(targetTypeDefinitions) {
      case function: ScFunction =>
        function.parameters match {
          case Seq(head) if head.getRealParameterType.exists(valueType.conforms) =>
            Seq(function)
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }(PostfixCandidate)

  private def targetTypeDefinitions: Seq[ScTypeDefinition] = valueType match {
    case ExtractClass(definition: ScTypeDefinition) =>
      (definition +: definition.supers.filterByType[ScTypeDefinition]) ++
        contextsOfType[ScTypeDefinition]
    case _ => Seq.empty
  }
}

private object HoogleFinder {
  object ExpressionToReplaceWithQualifier {
    def unapply(ref: ScReferenceExpression): Option[(ScExpression, ScExpression)] =
      ref.qualifier
        .map((ref, _))
        .orElse(invocationWithQualifier(ref))

    private def invocationWithQualifier(ref: ScReferenceExpression): Option[(ScExpression, ScExpression)] = {
      ref.getParent match {
        case invocation: MethodInvocation => invocation.thisExpr.map((invocation, _))
        case _ => None
      }
    }
  }

  final case class PostfixCandidate(elementToImport: ScFunction,
                                    override val classToImport: ScObject)
    extends GlobalPsiClassMemberResult(elementToImport, classToImport)(Function.const(NameAvailabilityState.AVAILABLE)) {

    override protected def buildItem(lookupItem: ScalaLookupItem): LookupElement =
      LookupElementBuilder
        .create(elementToImport)
        .withInsertHandler(createInsertHandler)
        .withRenderer((_, presentation) => lookupItem.renderElement(presentation))
        .withExpensiveRenderer(createExpensiveRenderer(lookupItem))

    override protected def createInsertHandler: InsertHandler[LookupElement] =
      (context: InsertionContext, _: LookupElement) => {
        context.getFile.findReferenceAt(context.getStartOffset) match {
          case ExpressionToReplaceWithQualifier(expressionToReplace, qualifier) =>
            val replacement = createExpressionWithContextFromText(
              elementToImport.name + "(" + qualifier.getText + ")",
              expressionToReplace.getContext,
              expressionToReplace
            )

            val ScMethodCall(methodReference: ScReferenceExpression, _) = expressionToReplace.replaceExpression(
              replacement,
              removeParenthesis = true
            )

            methodReference.bindToElement(
              elementToImport,
              Some(classToImport)
            )
          case _ =>
        }
      }

    private def createExpensiveRenderer(lookupItem: ScalaLookupItem): LookupElementRenderer[LookupElement] =
      (_, presentation) => {
        lookupItem.getExpensiveRenderer.asInstanceOf[LookupElementRenderer[LookupElement]].renderElement(lookupItem, presentation)

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
