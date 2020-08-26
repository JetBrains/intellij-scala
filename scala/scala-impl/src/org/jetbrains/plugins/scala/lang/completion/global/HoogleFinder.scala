package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation, LookupElementRenderer}
import org.jetbrains.plugins.scala.extensions.TraversableExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}
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
          case collection.Seq(head) if head.getRealParameterType.exists(valueType.conforms) =>
            Seq(function)
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }(PostfixCandidate)

  private def targetTypeDefinitions: collection.Seq[ScTypeDefinition] = valueType match {
    case ExtractClass(definition: ScTypeDefinition) =>
      (definition +: definition.supers.filterByType[ScTypeDefinition]) ++
        contextsOfType[ScTypeDefinition]
    case _ => Seq.empty
  }
}

private object HoogleFinder {

  final case class PostfixCandidate(elementToImport: ScFunction,
                                    override val classToImport: ScObject)
    extends GlobalMemberResult(elementToImport, classToImport)(Function.const(NameAvailabilityState.AVAILABLE)) {

    override protected def buildItem(lookupItem: ScalaLookupItem): LookupElement =
      LookupElementBuilder
        .create(elementToImport)
        .withInsertHandler(createInsertHandler)
        .withRenderer(createRenderer(lookupItem))

    override protected def createInsertHandler: InsertHandler[LookupElement] =
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
