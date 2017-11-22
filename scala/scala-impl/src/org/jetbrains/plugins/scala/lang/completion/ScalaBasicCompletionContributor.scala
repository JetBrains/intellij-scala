package org.jetbrains.plugins.scala
package lang
package completion

import scala.annotation.tailrec

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{InsertHandlerDecorator, LookupElement, LookupElementDecorator}
import com.intellij.openapi.editor.Document
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaRuntimeTypeEvaluator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.lookups.LookupElementManager.getLookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.weighter.ScalaCompletionSorting._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScTypeProjectionImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceExpressionImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompletionProcessor, ImplicitCompletionProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType

/**
 * @author Alexander Podkhalyuzin
 * Date: 16.05.2008
 */
abstract class ScalaCompletionContributor extends CompletionContributor {
  def positionFromParameters(parameters: CompletionParameters): PsiElement = ScalaCompletionUtil.positionFromParameters(parameters)

  override def fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
    val updatedSet = result
      .withBacktickMatcher()
      .withScalaSorting(parameters)
    super.fillCompletionVariants(parameters, updatedSet)
  }
}

class ScalaBasicCompletionContributor extends ScalaCompletionContributor {
  private val addedElements = collection.mutable.Set[String]()
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val dummyPosition = positionFromParameters(parameters)

      val node = Option(dummyPosition.getNode).getOrElse(return)

      val (position, inString, inInterpolatedString) = node.getElementType match {
        case ScalaTokenTypes.tIDENTIFIER | ScalaDocTokenType.DOC_TAG_VALUE_TOKEN => (dummyPosition, false, false)
        case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING =>
          //it's ok to use parameters here as we want just to calculate offset
          val offsetInString = parameters.getOffset - parameters.getPosition.getTextRange.getStartOffset + 1
          val interpolated =
            ScalaPsiElementFactory.createExpressionFromText("s" + dummyPosition.getText, dummyPosition.getContext)
          (interpolated.findElementAt(offsetInString), true, false)
        case ScalaTokenTypes.tINTERPOLATED_STRING | ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
          val position = dummyPosition.getContext
          if (!position.isInstanceOf[ScInterpolated]) return
          if (!parameters.getPosition.getParent.isInstanceOf[ScInterpolated]) return

          val interpolated = position.asInstanceOf[ScInterpolated]
          val dummyInterpolated = parameters.getPosition.getParent.asInstanceOf[ScInterpolated]

          //we use here file copy as we want to work with offsets.
          val offset = parameters.getOffset
          val dummyInjections = dummyInterpolated.getInjections
          val index = dummyInjections.lastIndexWhere { expr =>
            expr.getTextRange.getEndOffset <= offset
          }

          //it's ok to use parameters here as we want just to calculate offset
          val offsetInString = offset - dummyInterpolated.getTextRange.getStartOffset
          val res = ScalaBasicCompletionContributor.getStartEndPointForInterpolatedString(interpolated, index, offsetInString)
          if (res.isEmpty) return
          val (exprStartInString, endPoint) = res.get
          val stringText = interpolated.getText
          val newInterpolated =
            ScalaPsiElementFactory.createExpressionFromText(stringText.substring(0, exprStartInString) + "{" +
              stringText.substring(exprStartInString, endPoint) + "}" +
              stringText.substring(endPoint), position.getContext)
          (newInterpolated.findElementAt(offsetInString + 1), false, true)
        case _ => return
      }
      result.restartCompletionWhenNothingMatches()

      //if prefix is capitalized, class name completion is enabled
      val classNameCompletion = shouldRunClassNameCompletion(positionFromParameters(parameters), parameters, result.getPrefixMatcher)
      val insertedElement: PsiElement = position
      if (!inString && !inInterpolatedString && !ScalaPsiUtil.fileContext(insertedElement).isInstanceOf[ScalaFile]) return
      val lookingForAnnotations: Boolean =
        Option(insertedElement.getContainingFile findElementAt (insertedElement.getTextOffset - 1)) exists {
          _.getNode.getElementType == ScalaTokenTypes.tAT
        }

      var elementAdded = false
      def addElement(el: LookupElement) {
        if (result.getPrefixMatcher.prefixMatches(el))
          elementAdded = true
        result.addElement(el)
        addedElements += el.getLookupString
      }

      implicit val project = position.projectContext
      position.getContext match {
        case ref: ScReferenceElement =>
          val isInImport = ScalaPsiUtil.getContextOfType(ref, true, classOf[ScImportStmt]) != null
          def applyVariant(variant: Object, addElement: LookupElement => Unit = addElement) {
            variant match {
              case el: ScalaLookupItem if el.isValid =>
                if (inString) el.isInSimpleString = true
                if (inInterpolatedString) el.isInInterpolatedString = true
                val elem = el.element
                elem match {
                  case clazz: PsiClass =>
                    import scala.collection.mutable.{HashMap => MHashMap}
                    val renamedMap = new MHashMap[String, (String, PsiNamedElement)]
                    el.isRenamed.foreach(name => renamedMap += ((clazz.name, (name, clazz))))

                    if (!ScalaCompletionUtil.isExcluded(clazz) &&
                      !classNameCompletion && (!lookingForAnnotations || clazz.isAnnotationType)) {

                      val (typesAfterNew, isAfterNew) =
                        ScalaAfterNewCompletionUtil.expectedTypesAfterNew(position, context)

                      addElement(
                        if (isAfterNew) getLookupElementFromClass(typesAfterNew, clazz, renamedMap) else el
                      )
                    }
                  case _ if lookingForAnnotations =>
                  case f: FakePsiMethod if f.name.endsWith("_=") && parameters.getInvocationCount < 2 => //don't show _= methods for vars in basic completion
                  case _: ScFun => addElement(el)
                  case _: ScClassParameter =>
                    addElement(el)
                  case _: ScParameter if !el.isNamedParameter=>
                    el.isLocalVariable = true
                    addElement(el)
                  case patt: ScBindingPattern =>
                    val context = ScalaPsiUtil.nameContext(patt)
                    context match {
                      case sValue: ScValue if sValue.isLocal =>
                        el.isLocalVariable = true
                        addElement(el)
                      case sVar: ScVariable if sVar.isLocal =>
                        el.isLocalVariable = true
                        addElement(el)
                      case _: ScCaseClause | _: ScEnumerator | _: ScGenerator =>
                        el.isLocalVariable = true
                        addElement(el)
                      case memb: PsiMember =>
                        if (parameters.getInvocationCount > 1 ||
                          ResolveUtils.isAccessible(memb, position, forCompletion = true)) addElement(el)
                      case _ => addElement(el)
                    }
                  case memb: PsiMember =>
                    if (parameters.getInvocationCount > 1 || ResolveUtils.isAccessible(memb, position,
                      forCompletion = true))
                      addElement(el)
                  case _ =>
                    addElement(el)
                }
              case _ =>
            }
          }
          def postProcessMethod(resolveResult: ScalaResolveResult) {
            val probablyContinigClass = Option(PsiTreeUtil.getContextOfType(position, classOf[PsiClass]))
            val qualifierType = resolveResult.fromType.getOrElse(Nothing)
            val lookupItems: Seq[ScalaLookupItem] = getLookupElement(
              resolveResult,
              isInImport = isInImport,
              qualifierType = qualifierType,
              containingClass = probablyContinigClass,
              isInStableCodeReference = ref.isInstanceOf[ScStableCodeReferenceElement])
            lookupItems.foreach(applyVariant(_))
          }

          def kinds(ref: ScReferenceElement) = ref.getKinds(incomplete = false, completion = true)

          def completionProcessor(ref: ScReferenceElement): CompletionProcessor =
            new CompletionProcessor(kinds(ref), ref) {

              override protected def postProcess(result: ScalaResolveResult): Unit =
                postProcessMethod(result)
            }

          @tailrec
          def addThisAndSuper(elem: PsiElement): Unit = {
            elem match {
              case _: ScNewTemplateDefinition => //do nothing, impossible to invoke
              case t: ScTemplateDefinition =>
                addElement(new ScalaLookupItem(t, t.name + ".this"))
                addElement(new ScalaLookupItem(t, t.name + ".super"))
              case _ =>
            }
            val context = elem.getContext
            if (context != null) addThisAndSuper(context)
          }

          ref match {
            case refImpl: ScStableCodeReferenceElementImpl => refImpl.doResolve(completionProcessor(refImpl))
            case refImpl: ScReferenceExpressionImpl =>
              val processor = new ImplicitCompletionProcessor(kinds(ref), ref) {

                override protected def postProcess(result: ScalaResolveResult): Unit =
                  postProcessMethod(result)
              }

              refImpl.doResolve(processor)
              if (ScalaCompletionUtil.completeThis(refImpl))
                addThisAndSuper(refImpl)
            case refImpl: ScTypeProjectionImpl => refImpl.doResolve(completionProcessor(refImpl))
            case _ =>
              for (variant <- ref.asInstanceOf[PsiReference].getVariants) {
                applyVariant(variant)
              }
          }
          if (!elementAdded && !classNameCompletion && ScalaCompletionUtil.shouldRunClassNameCompletion(
            positionFromParameters(parameters), parameters,
            result.getPrefixMatcher, checkInvocationCount = false, lookingForAnnotations = lookingForAnnotations)) {
            ScalaClassNameCompletionContributor.completeClassName(dummyPosition, parameters, context, result)
          }

          //adds runtime completions for evaluate expression in debugger
          val runtimeQualifierType: ScType = getQualifierCastType(ref, parameters)
          if (runtimeQualifierType != null) {
            def addElementWithDecorator(elem: LookupElement, decorator: InsertHandlerDecorator[LookupElement]) {
              if (!addedElements.contains(elem.getLookupString)) {
                val newElem = LookupElementDecorator.withInsertHandler(elem, decorator)
                result.addElement(newElem)
                addedElements += elem.getLookupString
              }
            }

            createReferenceWithQualifierType(runtimeQualifierType, ref.getContext, ref) match {
              case refImpl: ScReferenceExpressionImpl =>
                val processor = new ImplicitCompletionProcessor(kinds(refImpl), refImpl) {

                  override protected def postProcess(result: ScalaResolveResult): Unit = {
                    val lookupItems = getLookupElement(
                      result,
                      isInImport = isInImport,
                      qualifierType = runtimeQualifierType,
                      isInStableCodeReference = ref.isInstanceOf[ScStableCodeReferenceElement],
                      isInSimpleString = inString,
                      isInInterpolatedString = inInterpolatedString
                    )
                    val decorator = castDecorator(runtimeQualifierType.canonicalText)
                    lookupItems.foreach(item => applyVariant(item, addElementWithDecorator(_, decorator)))
                  }
                }
                refImpl.doResolve(processor)
              case _ =>
            }
          }
        case _ =>
      }
      if (position.getNode.getElementType == ScalaDocTokenType.DOC_TAG_VALUE_TOKEN) result.stopHere()
    }
  })

  override def beforeCompletion(context: CompletionInitializationContext) {
    addedElements.clear()
    val offset: Int = context.getStartOffset - 1
    val file: PsiFile = context.getFile
    context.setDummyIdentifier(ScalaCompletionUtil.getDummyIdentifier(offset, file))
    super.beforeCompletion(context)
  }

  @Nullable
  private def getQualifierCastType(ref: ScReferenceElement, parameters: CompletionParameters): ScType = {
    ref match {
      case refExpr: ScReferenceExpression =>
        (for (qualifier <- refExpr.qualifier) yield {
          val evaluator = refExpr.getContainingFile.getCopyableUserData(ScalaRuntimeTypeEvaluator.KEY)
          if (evaluator != null) evaluator(qualifier) else null
        }).orNull
      case _ => null
    }
  }

  private def createReferenceWithQualifierType(qualType: ScType, context: PsiElement, child: PsiElement): ScReferenceElement = {
    val text =
      s"""|{
          |  val xxx: ${qualType.canonicalText} = null
          |  xxx.xxx
          |}""".stripMargin
    val block = ScalaPsiElementFactory.createExpressionWithContextFromText(text, context, child).asInstanceOf[ScBlock]
    block.exprs.last.asInstanceOf[ScReferenceElement]
  }

  private def castDecorator(canonTypeName: String) = new InsertHandlerDecorator[LookupElement] {
    def handleInsert(context: InsertionContext, item: LookupElementDecorator[LookupElement]) {
      val document: Document = context.getEditor.getDocument
      context.commitDocument()
      val file = PsiDocumentManager.getInstance(context.getProject).getPsiFile(document)
      val ref: ScReferenceElement =
        PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset, classOf[ScReferenceElement], false)
      if (ref != null) {
        ref.qualifier match {
          case None =>
          case Some(qual) =>
            val castString = s".asInstanceOf[$canonTypeName]"
            document.insertString(qual.getTextRange.getEndOffset, castString)
            context.commitDocument()
            ScalaPsiUtil.adjustTypes(file)
            PsiDocumentManager.getInstance(file.getProject).doPostponedOperationsAndUnblockDocument(document)
            context.getEditor.getCaretModel.moveToOffset(context.getTailOffset)
        }
      }
      item.getDelegate.handleInsert(context)
    }
  }
}

object ScalaBasicCompletionContributor {
  def getStartEndPointForInterpolatedString(interpolated: ScInterpolated, index: Int, offsetInString: Int): Option[(Int, Int)] = {
    val injections = interpolated.getInjections
    if (index != -1) {
      val expr = injections(index)
      if (expr.isInstanceOf[ScBlock]) return None
      val stringText = interpolated.getText
      val pointPosition = expr.getTextRange.getEndOffset - interpolated.getTextRange.getStartOffset
      if (stringText.charAt(pointPosition) == '.') {
        val restString = stringText.substring(pointPosition + 1)
        val lexer = new ScalaLexer()
        val noQuotes = if (interpolated.isMultiLineString) 3 else 1
        lexer.start(restString, 0, restString.length - noQuotes)
        if (lexer.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
          val endPoint = lexer.getTokenEnd + pointPosition + 1
          if (endPoint >= offsetInString) {
            val exprStartInString = expr.getTextRange.getStartOffset - interpolated.getTextRange.getStartOffset
            Some(exprStartInString, endPoint)
          } else None
        } else None
      } else None
    } else None
  }
}
