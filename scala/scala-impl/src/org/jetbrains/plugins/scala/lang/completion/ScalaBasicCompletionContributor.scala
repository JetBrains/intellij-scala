package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{InsertHandlerDecorator, LookupElement, LookupElementDecorator}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil._
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaRuntimeTypeEvaluator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScInterpolated, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType

import scala.annotation.tailrec
import scala.collection.{JavaConverters, mutable}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 16.05.2008
  */
class ScalaBasicCompletionContributor extends ScalaCompletionContributor {

  import ScalaAfterNewCompletionContributor._
  import ScalaBasicCompletionContributor._
  import ScalaCompletionUtil._

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(),
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val dummyPosition = positionFromParameters(parameters)

        val (isInSimpleString, isInInterpolatedString) = isInString(dummyPosition)
        val maybePosition = (isInSimpleString, isInInterpolatedString) match {
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

        if (!(isInSimpleString ||
          isInInterpolatedString ||
          getContextOfType(position, classOf[ScalaFile]) != null)) {
          return
        }

        val prefixMatcher = result.getPrefixMatcher
        //if prefix is capitalized, class name completion is enabled
        val classNameCompletion = shouldRunClassNameCompletion(dummyPosition, prefixMatcher)(parameters)
        val lookingForAnnotations: Boolean =
          position.getContainingFile.findElementAt(position.getTextOffset - 1) match {
            case null => false
            case element => element.getNode.getElementType == ScalaTokenTypes.tAT
          }

        position.getContext match {
          case reference: ScReferenceImpl =>
            object ValidItem {

              private val isInTypeElement = getContextOfType(position, classOf[ScTypeElement]) != null
              private val maybeExpectedTypes = expectedTypeAfterNew(position)(context)

              def unapply(item: ScalaLookupItem): Option[ScalaLookupItem] = item.element match {
                case definition: ScTypeDefinition if filterDuplications(definition, item.isInImport) => None
                case clazz: PsiClass if isExcluded(clazz) ||
                  classNameCompletion ||
                  (lookingForAnnotations && !clazz.isAnnotationType) => None
                case clazz: PsiClass =>
                  maybeExpectedTypes.map {
                    _.apply(clazz, createRenamePair(item).toMap)
                  }.orElse(Some(item))
                case _ if lookingForAnnotations => None
                case _: ScFun | _: ScClassParameter => Some(item)
                case parameter: ScParameter if !item.isNamedParameter =>
                  validLocalDefinitionItem(item, parameter)
                case pattern@(_: ScBindingPattern |
                              _: ScFieldId) =>
                  ScalaPsiUtil.nameContext(pattern) match {
                    case valueOrVariable: ScValueOrVariable if valueOrVariable.isLocal =>
                      validLocalDefinitionItem(item, valueOrVariable)
                    case ScCaseClause(Some(pattern), _, _) =>
                      validLocalDefinitionItem(item, pattern)
                    case patterned: ScPatterned =>
                      validLocalDefinitionItem(item, patterned)
                    case _ => Some(item)
                  }
                case _ => Some(item)
              }

              private def filterDuplications(definition: ScTypeDefinition, isInImport: Boolean) =
                definition.baseCompanionModule.isDefined &&
                  (definition match {
                    case _: ScObject => isInTypeElement
                    case _ => isInImport
                  })

              private def validLocalDefinitionItem(item: ScalaLookupItem, ancestor: ScalaPsiElement) =
                if (isAncestor(ancestor, position, true)) {
                  None
                } else {
                  item.isLocalVariable = true
                  Some(item)
                }
            }

            val processor = new PostProcessor(
              reference,
              isInSimpleString,
              isInInterpolatedString,
              parameters.getInvocationCount
            )
            reference.doResolve(processor)
            val defaultLookupElements = processor.lookupElements.flatMap {
              case ValidItem(item) => Some(item)
              case _ => None
            } ++ prefixedThisAndSupers(reference)

            import JavaConverters._
            result.addAllElements(defaultLookupElements.asJava)

            if (!defaultLookupElements.exists(prefixMatcher.prefixMatches)
              && !classNameCompletion
              && (lookingForAnnotations || shouldRunClassNameCompletion(dummyPosition, prefixMatcher, checkInvocationCount = false)(parameters))) {
              ScalaClassNameCompletionContributor.completeClassName(dummyPosition, result)(parameters, context)
            }

            //adds runtime completions for evaluate expression in debugger
            for {
              qualifierCastType <- qualifierCastType(reference)
              canonicalText = qualifierCastType.canonicalText
              newReference <- createReferenceWithQualifierType(canonicalText)(reference.getContext, reference)

              processor = new PostProcessor(
                newReference,
                isInSimpleString,
                isInInterpolatedString,
                invocationCount = 3 /* all */ ,
                qualifierType = Some(qualifierCastType)
              ) {

                private val lookupStrings = mutable.Set(defaultLookupElements.map(_.getLookupString): _*)
                private val decorator = insertHandlerDecorator(s".asInstanceOf[$canonicalText]")

                override protected def validLookupElement(result: ScalaResolveResult): Option[LookupElement] =
                  super.validLookupElement(result).filter {
                    // TODO support renamed classes
                    case ValidItem(item) => lookupStrings.add(item.getLookupString)
                    case _ => false
                  }.map {
                    case item: ScalaLookupItem => LookupElementDecorator.withInsertHandler(item, decorator)
                  }
              }
            } {
              newReference.doResolve(processor)
              val runtimeLookupElements = processor.lookupElements
              result.addAllElements(runtimeLookupElements.asJava)
            }
          case _ =>
        }
        if (position.getNode.getElementType == ScalaDocTokenType.DOC_TAG_VALUE_TOKEN) result.stopHere()
      }
    })

  override def beforeCompletion(context: CompletionInitializationContext): Unit = {
    context.setDummyIdentifier(dummyIdentifier(context.getFile, context.getStartOffset - 1))
    super.beforeCompletion(context)
  }
}

object ScalaBasicCompletionContributor {

  private class PostProcessor(override val getPlace: ScReference,
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
    private val isInImport = getContextOfType(getPlace, classOf[ScImportStmt]) != null
    private val isInStableCodeReference = getPlace.isInstanceOf[ScStableCodeReference]

    def lookupElements: Seq[LookupElement] = lookupElements_

    override protected final def postProcess(resolveResult: ScalaResolveResult): Unit = {
      lookupElements_ ++= validLookupElement(resolveResult)
    }

    protected def validLookupElement(result: ScalaResolveResult): Option[LookupElement] = result.element match {
      case element if isAccessible(element, result.isNamedParameter) =>
        result.getLookupElement(
          qualifierType = qualifierType,
          isInImport = isInImport,
          containingClass = containingClass,
          isInStableCodeReference = isInStableCodeReference,
          isInSimpleString = isInSimpleString,
          isInInterpolatedString = isInInterpolatedString
        )
      case _ => None
    }

    private def isAccessible(element: PsiNamedElement,
                             isNamedParameter: Boolean): Boolean =
      completion.regardlessAccessibility(invocationCount) ||
        (element match {
          case method: FakePsiMethod => !method.name.endsWith("_=") // TODO unify! // don't show _= methods for vars in basic completion
          case _: ScClassParameter if isNamedParameter => true
          case member: PsiMember => completion.isAccessible(member)(getPlace)
          case _ => true
        })
  }

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

        tokenizeLiteral(stringText.substring(pointPosition), interpolated.quoteLength).flatMap { tokenEnd =>
          tokenEnd + pointPosition match {
            case endPoint if endPoint >= startOffset - context.getTextRange.getStartOffset =>
              Some(rangeStartOffset - startOffset, endPoint)
            case _ => None
          }
        }
    }

  private def tokenizeLiteral(text: String, quoteLength: Int) = text.charAt(0) match {
    case '.' =>
      val lexer = new ScalaLexer()
      lexer.start(text, 1, text.length - quoteLength)
      lexer.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => Some(lexer.getTokenEnd)
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

  private def createReferenceWithQualifierType(canonicalText: String)
                                              (context: PsiElement, child: PsiElement): Option[ScReferenceExpression] = {
    val text =
      s"""{
         |  val xxx: $canonicalText = null
         |  xxx.xxx
         |}""".stripMargin
    ScalaPsiElementFactory.createExpressionWithContextFromText(text, context, child) match {
      case block: ScBlock => block.exprs.lastOption.collect {
        case expression: ScReferenceExpression => expression
      }
      case _ => None
    }
  }

  private def insertHandlerDecorator(text: String): InsertHandlerDecorator[ScalaLookupItem] =
    (context: InsertionContext, decorator: LookupElementDecorator[ScalaLookupItem]) => {
      val document = context.getEditor.getDocument
      context.commitDocument()

      val file = PsiDocumentManager.getInstance(context.getProject).getPsiFile(document)
      findElementOfClassAtOffset(file, context.getStartOffset, classOf[ScReference], false) match {
        case null =>
        case ScReference.qualifier(qualifier) =>
          document.insertString(qualifier.getTextRange.getEndOffset, text)
          context.commitDocument()

          ScalaPsiUtil.adjustTypes(file)
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
