package org.jetbrains.plugins.scala.lang
package completion

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet, InsertionContext}
import com.intellij.codeInsight.lookup.{InsertHandlerDecorator, LookupElement, LookupElementDecorator}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.{findElementOfClassAtOffset, getContextOfType, isAncestor}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaRuntimeTypeEvaluator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{adjustTypes, nameContext}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScInterpolated, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScPatterned, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createExpressionWithContextFromText}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScReferenceExpressionImpl, ScReferenceImpl}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType.DOC_TAG_VALUE_TOKEN

import scala.annotation.tailrec
import scala.collection.{JavaConverters, mutable}

private class ScalaBasicCompletionProvider extends CompletionProvider[CompletionParameters] {
  import ScalaBasicCompletionProvider._
  import ScalaCompletionUtil._

  override def addCompletions(parameters: CompletionParameters,
                              context: ProcessingContext,
                              result: CompletionResultSet): Unit = {
    val dummyPosition = positionFromParameters(parameters)

    val (isInSimpleString, isInInterpolatedString) = isInString(dummyPosition)
    val maybePosition = (isInSimpleString, isInInterpolatedString) match {
      case (true, true) => return
      case (true, _) =>
        Some(("s" + dummyPosition.getText, dummyPosition))
      case (_, true) =>
        val context = dummyPosition.getContext
        splitInterpolatedString(context, parameters.getOffset) match {
          case Some(text) => Some((text, context))
          case _ => return
        }
      case _ => None
    }

    val position = maybePosition.fold(dummyPosition) {
      case (text, offsetContext) =>
        createExpressionFromText(text, offsetContext.getContext)
          .findElementAt(parameters.getOffset - offsetContext.getTextRange.getStartOffset + 1)
    }

    result.restartCompletionWhenNothingMatches()

    if (!isInScalaContext(position, isInSimpleString, isInInterpolatedString)) return

    val prefixMatcher = result.getPrefixMatcher
    //if prefix is capitalized, class name completion is enabled
    val classNameCompletion = shouldRunClassNameCompletion(dummyPosition, prefixMatcher)(parameters)
    val annotationsOnly = annotationPattern.accepts(position, context)

    position.getContext match {
      case reference: ScReferenceImpl =>
        val processor = new PostProcessor(
          reference,
          isInSimpleString,
          isInInterpolatedString,
          parameters.getInvocationCount
        )

        import ScalaAfterNewCompletionContributor._
        val maybeExpectedTypes = expectedTypeAfterNew(position, context)

        val defaultLookupElements = processor.lookupElements.filter {
          case ScalaLookupItem(_, clazz: PsiClass) =>
            !classNameCompletion &&
              (!annotationsOnly || clazz.isAnnotationType)
          case _ => !annotationsOnly
        }.map {
          case ScalaLookupItem(item, clazz: PsiClass) =>
            maybeExpectedTypes.fold(item: LookupElement) { constructor =>
              constructor(clazz).createLookupElement(item.isRenamed)
            }
          case item => item
        }

        import JavaConverters._
        result.addAllElements(defaultLookupElements.asJava)

        ProgressManager.checkCanceled()
        result.addAllElements(prefixedThisAndSupers(reference).asJava)

        if (!defaultLookupElements.exists(prefixMatcher.prefixMatches)
          && !classNameCompletion
          && (annotationsOnly || shouldRunClassNameCompletion(dummyPosition, prefixMatcher, checkInvocationCount = false)(parameters))) {
          ScalaClassNameCompletionContributor.completeClassName(dummyPosition, result)(parameters, context)
        }

        ProgressManager.checkCanceled()
        //adds runtime completions for evaluate expression in debugger
        for {
          qualifierCastType <- qualifierCastType(reference)
          canonicalText = qualifierCastType.canonicalText
          newReference <- createReferenceWithQualifierType(canonicalText, reference)

          processor = new PostProcessor(
            newReference,
            isInSimpleString,
            isInInterpolatedString,
            invocationCount = 3 /* all */ ,
            qualifierType = Some(qualifierCastType)
          ) {

            private val lookupStrings = mutable.Set(defaultLookupElements.map(_.getLookupString): _*)
            private val decorator = insertHandlerDecorator(canonicalText)

            override protected def validLookupElement(result: ScalaResolveResult): Option[LookupElement] = for {
              element <- super.validLookupElement(result)
              if lookupStrings.add(element.getLookupString) // TODO support renamed classes
            } yield LookupElementDecorator.withInsertHandler(element, decorator)
          }
        } result.addAllElements(processor.lookupElements.asJava)
      case _ =>
    }
    if (position.getNode.getElementType == DOC_TAG_VALUE_TOKEN) result.stopHere()
  }
}

object ScalaBasicCompletionProvider {

  import ScalaTokenTypes._

  private class PostProcessor(override val getPlace: ScReferenceImpl,
                              private val isInSimpleString: Boolean,
                              private val isInInterpolatedString: Boolean,
                              private val invocationCount: Int,
                              private val qualifierType: Option[ScType] = None)
    extends CompletionProcessor(
      getPlace.getKinds(incomplete = false, completion = true),
      getPlace,
      isImplicit = getPlace.isInstanceOf[ScReferenceExpression]
    ) {

    private val lookupElements_ = mutable.ArrayBuffer.empty[LookupElement]

    private val containingClass = Option(getContextOfType(getPlace, classOf[PsiClass]))
    private val isInImport = completion.isInImport(getPlace)
    private val isInTypeElement = completion.isInTypeElement(getPlace)
    private val isInStableCodeReference = getPlace.isInstanceOf[ScStableCodeReference]

    final def lookupElements: Seq[LookupElement] = {
      ProgressManager.checkCanceled()
      getPlace.doResolve(this)

      ProgressManager.checkCanceled()
      lookupElements_
    }

    override protected final def postProcess(resolveResult: ScalaResolveResult): Unit = {
      lookupElements_ ++= validLookupElement(resolveResult)
    }

    protected def validLookupElement(result: ScalaResolveResult): Option[LookupElement] = result.element match {
      case element if element.isValid && isAccessible(element, result.isNamedParameter) =>
        isApplicable(element, result.isNamedParameter).map { isLocalVariable =>
          result.getLookupElement(
            qualifierType = qualifierType,
            isInImport = isInImport,
            containingClass = containingClass,
            isInStableCodeReference = isInStableCodeReference,
            isLocalVariable = isLocalVariable,
            isInSimpleString = isInSimpleString,
            isInInterpolatedString = isInInterpolatedString
          ).get
        }
      case _ => None
    }

    private def isApplicable(element: PsiNamedElement,
                             isNamedParameter: Boolean): Option[Boolean] = element match {
      case clazz: PsiClass if isExcluded(clazz) => None
      case definition: ScTypeDefinition if filterDuplications(definition) => None
      case parameter: ScClassParameter =>
        isValidLocalDefinition(parameter, isLocal = false)
      case parameter: ScParameter if !isNamedParameter =>
        isValidLocalDefinition(parameter)
      case pattern@(_: ScBindingPattern |
                    _: ScFieldId) =>
        val context = nameContext(pattern) match {
          case valueOrVariable: ScValueOrVariable if valueOrVariable.isLocal => valueOrVariable
          case ScCaseClause(Some(pattern), _, _) => pattern
          case patterned: ScPatterned => patterned
          case _ => null
        }

        context match {
          case null => Some(false)
          case _ => isValidLocalDefinition(context)
        }
      case _ => Some(false)
    }

    private def filterDuplications(definition: ScTypeDefinition) =
      definition.baseCompanionModule.isDefined &&
        (definition match {
          case _: ScObject => isInTypeElement
          case _ => isInImport
        })

    private def isValidLocalDefinition(element: PsiElement,
                                       isLocal: Boolean = true): Option[Boolean] =
      if (isAncestor(element, getPlace, true))
        None
      else
        Some(isLocal)

    private def isAccessible(element: PsiNamedElement,
                             isNamedParameter: Boolean): Boolean =
      regardlessAccessibility(invocationCount) ||
        (element match {
          case method: FakePsiMethod => !method.name.endsWith("_=") // TODO unify! // don't show _= methods for vars in basic completion
          case _: ScClassParameter if isNamedParameter => true
          case member: PsiMember => completion.isAccessible(member)(getPlace)
          case _ => true
        })
  }

  private def splitInterpolatedString(context: PsiElement,
                                      offset: Int): Option[String] =
    context match {
      case interpolated: ScInterpolated =>
        interpolatedStringBounds(interpolated, offset).map {
          case (origin, bound) =>
            val text = interpolated.getText
            text.substring(0, origin) +
              "{" + text.substring(origin, bound) + "}" +
              text.substring(bound)
        }
      case _ => None
    }

  private def isInString(position: PsiElement) = position.getNode match {
    case null => (true, true)
    case node =>
      node.getElementType match {
        case `tIDENTIFIER` |
             DOC_TAG_VALUE_TOKEN =>
          (false, false)
        case `tSTRING` |
             `tMULTILINE_STRING` =>
          (true, false)
        case `tINTERPOLATED_STRING` |
             `tINTERPOLATED_MULTILINE_STRING` =>
          (false, true)
        case _ =>
          (true, true)
      }
  }

  def interpolatedStringBounds(interpolated: ScInterpolated,
                               offset: Int): Option[(Int, Int)] =
    interpolated.getInjections.reverseIterator
      .find(_.getTextRange.getEndOffset <= offset)
      .filterNot(_.isInstanceOf[ScBlock])
      .map(_.getTextRange)
      .flatMap { range =>
        val stringText = interpolated.getText

        val startOffset = interpolated.getTextRange.getStartOffset
        val pointPosition = range.getEndOffset - startOffset

        tokenizeLiteral(stringText.substring(pointPosition), interpolated.quoteLength).flatMap { tokenEnd =>
          val endPoint = tokenEnd + pointPosition
          if (endPoint >= 0) Some(range.getStartOffset - startOffset, endPoint)
          else None
        }
      }

  private def tokenizeLiteral(text: String, quoteLength: Int) = text.charAt(0) match {
    case '.' =>
      val lexer = new ScalaLexer()
      lexer.start(text, 1, text.length - quoteLength)
      lexer.getTokenType match {
        case `tIDENTIFIER` => Some(lexer.getTokenEnd)
        case _ => None
      }
    case _ => None
  }

  private def qualifierCastType(reference: ScReference): Option[ScType] = reference match {
    case ScReferenceExpression.withQualifier(qualifier) =>
      reference.getContainingFile.getCopyableUserData(ScalaRuntimeTypeEvaluator.KEY) match {
        case null => None
        case evaluator => Option(evaluator(qualifier))
      }
    case _ => None
  }

  private def createReferenceWithQualifierType(canonicalText: String,
                                               reference: ScReference) = {
    val text =
      s"""{
         |  val xxx: $canonicalText = null
         |  xxx.xxx
         |}""".stripMargin
    createExpressionWithContextFromText(text, reference.getContext, reference) match {
      case block: ScBlock => block.exprs.lastOption.filterByType[ScReferenceExpressionImpl]
      case _ => None
    }
  }

  private def insertHandlerDecorator(text: String): InsertHandlerDecorator[LookupElement] =
    (context: InsertionContext, decorator: LookupElementDecorator[LookupElement]) => {
      val document = context.getEditor.getDocument
      context.commitDocument()

      val file = PsiDocumentManager.getInstance(context.getProject).getPsiFile(document)
      findElementOfClassAtOffset(file, context.getStartOffset, classOf[ScReference], false) match {
        case null =>
        case ScReference.qualifier(qualifier) =>
          document.insertString(qualifier.getTextRange.getEndOffset, s".asInstanceOf[$text]")
          context.commitDocument()

          adjustTypes(file)
          PsiDocumentManager.getInstance(file.getProject).doPostponedOperationsAndUnblockDocument(document)
          context.getEditor.getCaretModel.moveToOffset(context.getTailOffset)
        case _ =>
      }

      decorator.getDelegate.handleInsert(context)
    }

  private def prefixedThisAndSupers(reference: ScReference): List[ScalaLookupItem] = reference match {
    case expression: ScReferenceExpression if ScalaCompletionUtil.completeThis(expression) =>
      val notInsideSeveralClasses = expression.contexts.instancesOf[ScTemplateDefinition].size <= 1

      @tailrec
      def syntheticItems(element: PsiElement, result: List[ScalaLookupItem] = Nil): List[ScalaLookupItem] = element match {
        case null => result
        case td: ScTypeDefinition =>
          val superItem =
            if (td.extendsBlock.templateParents.isEmpty) Nil
            else List(new ScalaLookupItem(td, td.name + ".super"))
          val thisItem =
            if (notInsideSeveralClasses || td.isInstanceOf[ScObject]) Nil
            else List(new ScalaLookupItem(td, td.name + ".this"))

          syntheticItems(element.getContext, superItem ::: thisItem ::: result)
        case _ =>
          syntheticItems(element.getContext, result)
      }

      syntheticItems(expression)
    case _ => Nil
  }
}