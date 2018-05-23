package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{InsertHandlerDecorator, LookupElementDecorator}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaRuntimeTypeEvaluator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.weighter.ScalaCompletionSorting._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScTypeProjectionImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceExpressionImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * @author Alexander Podkhalyuzin
  *         Date: 16.05.2008
  */
abstract class ScalaCompletionContributor extends CompletionContributor {

  override def fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
    val updatedSet = result
      .withBacktickMatcher()
      .withScalaSorting(parameters)
    super.fillCompletionVariants(parameters, updatedSet)
  }
}

class ScalaBasicCompletionContributor extends ScalaCompletionContributor {

  import ScalaBasicCompletionContributor._

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(),
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val dummyPosition = positionFromParameters(parameters)

        val (inString, inInterpolatedString) = isInString(dummyPosition)
        val maybePosition = (inString, inInterpolatedString) match {
          case (true, true) => return
          case (true, _) =>
            Some(("s" + dummyPosition.getText, dummyPosition, parameters.getPosition))
          case (_, true) =>
            (dummyPosition.getContext, parameters.getPosition.getParent) match {
              case (interpolated: ScInterpolated, dummyInterpolated: ScInterpolated) =>
                splitInterpolatedString(interpolated, parameters.getOffset, dummyInterpolated) match {
                  case Some((first, second, third)) =>
                    Some((s"$first{$second}$third", interpolated, dummyInterpolated))
                  case _ => return
                }
              case _ => return
            }
          case _ => None
        }

        val position = maybePosition match {
          case Some((text, projectContext, offsetContext)) =>
            ScalaPsiElementFactory.createExpressionFromText(text, projectContext.getContext)
              .findElementAt(parameters.getOffset - offsetContext.getTextRange.getStartOffset + 1)
          case _ => dummyPosition
        }

        result.restartCompletionWhenNothingMatches()
        if (!inString && !inInterpolatedString && !ScalaPsiUtil.fileContext(position).isInstanceOf[ScalaFile]) return

        //if prefix is capitalized, class name completion is enabled
        val classNameCompletion = shouldRunClassNameCompletion(dummyPosition, result.getPrefixMatcher)(parameters)
        val lookingForAnnotations: Boolean =
          Option(position.getContainingFile.findElementAt(position.getTextOffset - 1)).exists {
            _.getNode.getElementType == ScalaTokenTypes.tAT
          }

        var elementAdded = false

        def addElement(item: ScalaLookupItem): Unit = {
          elementAdded |= result.getPrefixMatcher.prefixMatches(item)
          result.addElement(item)
        }

        position.getContext match {
          case ref: ScReferenceElement =>
            def validItem(item: ScalaLookupItem): Option[ScalaLookupItem] = if (item.isValid) {
              if (inString) item.isInSimpleString = true
              if (inInterpolatedString) item.isInInterpolatedString = true

              item.element match {
                case clazz: PsiClass =>
                  if (!isExcluded(clazz) && !classNameCompletion && (!lookingForAnnotations || clazz.isAnnotationType)) {

                    val lookupElement = expectedTypeAfterNew(position)(context)
                      .map(_.apply(clazz, createRenamePair(item).toMap))
                      .getOrElse(item)

                    Some(lookupElement)
                  } else None
                case _ if lookingForAnnotations => None
                case f: FakePsiMethod if f.name.endsWith("_=") && parameters.getInvocationCount < 2 => None //don't show _= methods for vars in basic completion
                case _: ScFun | _: ScClassParameter => Some(item)
                case _: ScParameter if !item.isNamedParameter =>
                  item.isLocalVariable = true
                  Some(item)
                case pattern: ScBindingPattern =>
                  ScalaPsiUtil.nameContext(pattern) match {
                    case valueOrVariable: ScValueOrVariable if valueOrVariable.isLocal =>
                      item.isLocalVariable = true
                      Some(item)
                    case _: ScCaseClause | _: ScEnumerator | _: ScGenerator =>
                      item.isLocalVariable = true
                      Some(item)
                    case member: PsiMember =>
                      if (parameters.getInvocationCount > 1 || ResolveUtils.isAccessible(member, position, forCompletion = true)) Some(item)
                      else None
                    case _ => Some(item)
                  }
                case member: PsiMember =>
                  if (parameters.getInvocationCount > 1 || ResolveUtils.isAccessible(member, position, forCompletion = true)) Some(item)
                  else None
                case _ => Some(item)
              }
            } else None

            trait PostProcessor {

              this: CompletionProcessor =>

              private val containingClass = Option(PsiTreeUtil.getContextOfType(position, classOf[PsiClass]))
              private val isInImport = PsiTreeUtil.getContextOfType(ref, classOf[ScImportStmt]) != null
              private val isInStableCodeReference = ref.isInstanceOf[ScStableCodeReferenceElement]

              override protected def postProcess(resolveResult: ScalaResolveResult): Unit = {
                lookupItems(resolveResult).foreach(addElement)
              }

              protected def lookupItems(result: ScalaResolveResult,
                                        qualifierType: Option[ScType] = None): Seq[ScalaLookupItem] =
                result.getLookupElement(
                  qualifierType = qualifierType,
                  isInImport = isInImport,
                  containingClass = containingClass,
                  isInStableCodeReference = isInStableCodeReference,
                  isInSimpleString = inString,
                  isInInterpolatedString = inInterpolatedString
                ).flatMap(validItem)
            }

            ref match {
              case refImpl: ScStableCodeReferenceElementImpl =>
                val processor = new CompletionProcessor(kinds(refImpl), refImpl) with PostProcessor
                refImpl.doResolve(processor)
              case refImpl: ScReferenceExpressionImpl =>
                val processor = new CompletionProcessor(kinds(refImpl), refImpl, isImplicit = true) with PostProcessor
                refImpl.doResolve(processor)
                prefixedThisAndSupers(refImpl).foreach(addElement)
              case refImpl: ScTypeProjectionImpl =>
                val processor = new CompletionProcessor(kinds(refImpl), refImpl) with PostProcessor
                refImpl.doResolve(processor)
              case _ =>
                (ref: PsiReference).getVariants.collect {
                  case item: ScalaLookupItem => item
                }.flatMap(validItem).foreach(addElement)
            }

            if (!elementAdded && !classNameCompletion) {
              ScalaClassNameCompletionContributor.completeClassName(
                result,
                checkInvocationCount = false,
                lookingForAnnotations = lookingForAnnotations
              )(parameters, context)
            }

            //adds runtime completions for evaluate expression in debugger
            for {
              qualifierType <- qualifierCastType(ref)
              canonicalText = qualifierType.canonicalText
              reference <- createReferenceWithQualifierType(canonicalText)(ref.getContext, ref)

              processor = new CompletionProcessor(kinds(reference), reference, isImplicit = true) with PostProcessor {

                private val lookupStrings = mutable.Set[String]()
                private val decorator = castDecorator(canonicalText)

                override protected def postProcess(resolveResult: ScalaResolveResult): Unit =
                  lookupItems(resolveResult, Some(qualifierType)).foreach { item =>
                    if (lookupStrings.add(item.getLookupString)) {
                      result.addElement(LookupElementDecorator.withInsertHandler(item, decorator))
                    }
                  }

              }
            } reference.doResolve(processor)
          case _ =>
        }
        if (position.getNode.getElementType == ScalaDocTokenType.DOC_TAG_VALUE_TOKEN) result.stopHere()
      }
    })

  override def beforeCompletion(context: CompletionInitializationContext): Unit = {
    context.setDummyIdentifier(getDummyIdentifier(context.getStartOffset - 1, context.getFile))
    super.beforeCompletion(context)
  }
}

object ScalaBasicCompletionContributor {

  private def splitInterpolatedString(interpolated: ScInterpolated,
                                      offset: Int,
                                      context: ScInterpolated): Option[(String, String, String)] =
    interpolatedStringBounds(interpolated, offset, context) match {
      case Some((origin, bound)) =>
        val text = interpolated.getText
        Some((
          text.substring(0, origin),
          text.substring(origin, bound),
          text.substring(bound)
        ))
      case _ => None
    }

  private def isInString(position: PsiElement) = position.getNode match {
    case null => (true, true)
    case node =>
      node.getElementType match {
        case ScalaTokenTypes.tIDENTIFIER | ScalaDocTokenType.DOC_TAG_VALUE_TOKEN => (false, false)
        case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING => (true, false)
        case ScalaTokenTypes.tINTERPOLATED_STRING | ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING => (false, true)
        case _ => (true, true)
      }
  }

  def interpolatedStringBounds(interpolated: ScInterpolated,
                               offset: Int,
                               context: ScInterpolated): Option[(Int, Int)] =
    interpolated.getInjections.reverseIterator
      .find(_.getTextRange.getEndOffset <= offset)
      .collect {
        case expression if !expression.isInstanceOf[ScBlock] =>
          val range = expression.getTextRange
          (range.getStartOffset, range.getEndOffset)
      }.flatMap {
      case (rangeStartOffset, rangeEndOffset) =>
        val stringText = interpolated.getText

        val startOffset = interpolated.getTextRange.getStartOffset
        val pointPosition = rangeEndOffset - startOffset

        tokenizeLiteral(stringText.substring(pointPosition), interpolated.isMultiLineString).flatMap { tokenEnd =>
          tokenEnd + pointPosition + 1 match {
            case endPoint if endPoint >= startOffset - context.getTextRange.getStartOffset =>
              Some(rangeStartOffset - startOffset, endPoint)
            case _ => None
          }
        }
    }

  private def tokenizeLiteral(text: String, isMultiLineString: Boolean) = text.charAt(0) match {
    case '.' =>
      val lexer = new ScalaLexer()
      lexer.start(text, 1, text.length - 1 - (if (isMultiLineString) 3 else 1))
      lexer.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => Some(lexer.getTokenEnd)
        case _ => None
      }
    case _ => None
  }

  private def qualifierCastType(reference: ScReferenceElement): Option[ScType] = reference match {
    case ScReferenceExpression.withQualifier(qualifier) =>
      reference.getContainingFile.getCopyableUserData(ScalaRuntimeTypeEvaluator.KEY) match {
        case null => None
        case evaluator => Option(evaluator(qualifier))
      }
    case _ => None
  }

  private def createReferenceWithQualifierType(canonicalText: String)
                                              (context: PsiElement, child: PsiElement): Option[ScReferenceExpression] = {
    val text =
      s"""{
         |  val xxx: $canonicalText = null
         |  xxx.xxx
         |}""".stripMargin
    ScalaPsiElementFactory.createOptionExpressionWithContextFromText(text, context, child).flatMap {
      case block: ScBlock => block.exprs.lastOption
      case _ => None
    }.collect {
      case expression: ScReferenceExpression => expression
    }
  }

  private def castDecorator(canonicalText: String): InsertHandlerDecorator[ScalaLookupItem] =
    (context: InsertionContext, decorator: LookupElementDecorator[ScalaLookupItem]) => {
      val document = context.getEditor.getDocument
      context.commitDocument()

      val file = PsiDocumentManager.getInstance(context.getProject).getPsiFile(document)
      PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset, classOf[ScReferenceElement], false) match {
        case null =>
        case ScReferenceElement.withQualifier(qualifier) =>
          document.insertString(qualifier.getTextRange.getEndOffset, s".asInstanceOf[$canonicalText]")
          context.commitDocument()

          ScalaPsiUtil.adjustTypes(file)
          PsiDocumentManager.getInstance(file.getProject).doPostponedOperationsAndUnblockDocument(document)
          context.getEditor.getCaretModel.moveToOffset(context.getTailOffset)
        case _ =>
      }

      decorator.getDelegate.handleInsert(context)
    }

  private def kinds(reference: ScReferenceElement) = reference.getKinds(incomplete = false, completion = true)

  private def prefixedThisAndSupers(expression: ScReferenceExpression): List[ScalaLookupItem] = {
    val isInsideSeveralClasses = expression.contexts.filterByType[ScTemplateDefinition].size > 1

    @tailrec
    def syntheticItems(element: PsiElement, result: List[ScalaLookupItem] = Nil): List[ScalaLookupItem] = {
      element match {
        case null => result
        case td: ScTypeDefinition =>
          val superItem =
            if (td.extendsBlock.templateParents.isEmpty) Nil
            else List(new ScalaLookupItem(td, td.name + ".super"))
          val thisItem =
            if (isInsideSeveralClasses && !td.isInstanceOf[ScObject])
              List(new ScalaLookupItem(td, td.name + ".this"))
            else Nil

          syntheticItems(element.getContext, superItem ::: thisItem ::: result)
        case _ =>
          syntheticItems(element.getContext, result)
      }
    }

    if (completeThis(expression)) syntheticItems(expression) else Nil
  }
}
