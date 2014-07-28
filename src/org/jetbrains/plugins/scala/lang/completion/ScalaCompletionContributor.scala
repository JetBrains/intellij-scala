package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import lookups.{ScalaLookupItem, LookupElementManager}
import psi.api.ScalaFile
import com.intellij.util.ProcessingContext
import com.intellij.patterns.PlatformPatterns
import lexer.ScalaTokenTypes
import scala.util.Random
import psi.api.statements.ScFun
import psi.api.base.patterns.ScBindingPattern
import psi.ScalaPsiUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.application.ApplicationManager
import refactoring.util.ScalaNamesUtil
import psi.impl.base.ScStableCodeReferenceElementImpl
import lang.resolve.processor.CompletionProcessor
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import lang.resolve.{ScalaResolveResult, ResolveUtils}
import psi.impl.expr.ScReferenceExpressionImpl
import psi.impl.base.types.ScTypeProjectionImpl
import com.intellij.codeInsight.lookup.{LookupElementDecorator, InsertHandlerDecorator, LookupElement}
import psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScReferenceExpression, ScNewTemplateDefinition}
import psi.types.{ScAbstractType, ScType}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import psi.api.statements.params.ScClassParameter
import psi.api.base.{ScStableCodeReferenceElement, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import psi.api.toplevel.typedef.ScTemplateDefinition
import extensions.toPsiNamedElementExt
import scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import scala.annotation.tailrec
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaRuntimeTypeEvaluator
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.editor.Document

/**
 * @author Alexander Podkhalyuzin
 * Date: 16.05.2008
 */

class ScalaCompletionContributor extends CompletionContributor {
  private val addedElements = collection.mutable.Set[String]()
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val elementType = parameters.getPosition.getNode.getElementType
      if (elementType != ScalaTokenTypes.tIDENTIFIER &&
          elementType != ScalaDocTokenType.DOC_TAG_VALUE_TOKEN) return
      result.restartCompletionWhenNothingMatches()
      val expectedTypesAfterNew: Array[ScType] =
      if (afterNewPattern.accepts(parameters.getPosition, context)) {
        val element = parameters.getPosition
        val newExpr: ScNewTemplateDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScNewTemplateDefinition])
        newExpr.expectedTypes().map {
          case ScAbstractType(_, lower, upper) => upper
          case tp                              => tp
        }
      } else Array.empty
      //if prefix is capitalized, class name completion is enabled
      val classNameCompletion = shouldRunClassNameCompletion(parameters, result.getPrefixMatcher)
      val insertedElement: PsiElement = parameters.getPosition
      if (!insertedElement.getContainingFile.isInstanceOf[ScalaFile]) return
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

      parameters.getPosition.getParent match {
        case ref: ScReferenceElement => {
          val isInImport = ScalaPsiUtil.getParentOfType(ref, classOf[ScImportStmt]) != null
          def applyVariant(variant: Object, addElement: LookupElement => Unit = addElement) {
            variant match {
              case el: ScalaLookupItem => {
                val elem = el.element
                elem match {
                  case clazz: PsiClass =>
                    import collection.mutable.{HashMap => MHashMap}
                    val renamedMap = new MHashMap[String, (String, PsiNamedElement)]
                    el.isRenamed.foreach(name => renamedMap += ((clazz.name, (name, clazz))))
                    val isExcluded: Boolean = ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
                      def compute: Boolean = {
                        JavaCompletionUtil.isInExcludedPackage(clazz, false)
                      }
                    })

                    if (!isExcluded && !classNameCompletion && (!lookingForAnnotations || clazz.isAnnotationType)) {
                      if (afterNewPattern.accepts(parameters.getPosition, context)) {
                        addElement(getLookupElementFromClass(expectedTypesAfterNew, clazz, renamedMap))
                      } else {
                        addElement(el)
                      }
                    }
                  case _ if lookingForAnnotations =>
                  case f: FakePsiMethod if f.name.endsWith("_=") && parameters.getInvocationCount < 2 => //don't show _= methods for vars in basic completion
                  case fun: ScFun => addElement(el)
                  case param: ScClassParameter =>
                    addElement(el)
                  case patt: ScBindingPattern => {
                    val context = ScalaPsiUtil.nameContext(patt)
                    context match {
                      case memb: PsiMember => {
                        if (parameters.getInvocationCount > 1 ||
                          ResolveUtils.isAccessible(memb, parameters.getPosition, forCompletion = true)) addElement(el)
                      }
                      case _ => addElement(el)
                    }
                  }
                  case memb: PsiMember => {
                    if (parameters.getInvocationCount > 1 || ResolveUtils.isAccessible(memb, parameters.getPosition,
                      forCompletion = true))
                      addElement(el)
                  }
                  case _ => addElement(el)
                }
              }
              case _ =>
            }
          }
          def postProcessMethod(resolveResult: ScalaResolveResult) {
            import org.jetbrains.plugins.scala.lang.psi.types.Nothing
            val qualifierType = resolveResult.fromType.getOrElse(Nothing)
            val lookupItems: Seq[ScalaLookupItem] = LookupElementManager.getLookupElement(
              resolveResult,
              isInImport = isInImport,
              qualifierType = qualifierType,
              isInStableCodeReference = ref.isInstanceOf[ScStableCodeReferenceElement])
            lookupItems.foreach(applyVariant(_))
          }

          def completionProcessor(ref: ScReferenceElement,
                                  collectImplicit: Boolean = false,
                                  postProcess: ScalaResolveResult => Unit = postProcessMethod): CompletionProcessor =
            new CompletionProcessor(ref.getKinds(incomplete = false, completion = true), ref, collectImplicit, postProcess = postProcess)

          @tailrec
          def addThisAndSuper(elem: PsiElement): Unit = {
            elem match {
              case t: ScNewTemplateDefinition => //do nothing, impossible to invoke
              case t: ScTemplateDefinition =>
                addElement(new ScalaLookupItem(t, t.name + ".this"))
                addElement(new ScalaLookupItem(t, t.name + ".super"))
              case _ =>
            }
            val context = elem.getContext
            if (context != null) addThisAndSuper(context)
          }

          ref match {
            case refImpl: ScStableCodeReferenceElementImpl => refImpl.doResolve(refImpl, completionProcessor(refImpl))
            case refImpl: ScReferenceExpressionImpl =>
              refImpl.doResolve(refImpl, completionProcessor(refImpl, collectImplicit = true))
              if (ScalaCompletionUtil.completeThis(refImpl))
                addThisAndSuper(refImpl)
            case refImpl: ScTypeProjectionImpl => refImpl.doResolve(completionProcessor(refImpl))
            case _ =>
              for (variant <- ref.asInstanceOf[PsiReference].getVariants) {
                applyVariant(variant)
              }
          }
          if (!elementAdded && !classNameCompletion && ScalaCompletionUtil.shouldRunClassNameCompletion(parameters,
            result.getPrefixMatcher, checkInvocationCount = false, lookingForAnnotations = lookingForAnnotations)) {
            ScalaClassNameCompletionContributor.completeClassName(parameters, context, result)
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
            def postProcess(resolveResult: ScalaResolveResult): Unit = {
              val lookupItems: Seq[ScalaLookupItem] = LookupElementManager.getLookupElement(
                resolveResult,
                isInImport = isInImport,
                qualifierType = runtimeQualifierType,
                isInStableCodeReference = ref.isInstanceOf[ScStableCodeReferenceElement])
              val decorator = castDecorator(runtimeQualifierType.canonicalText)
              lookupItems.foreach(item => applyVariant(item, addElementWithDecorator(_, decorator)))
            }

            val newRef = createReferenceWithQualifierType(runtimeQualifierType, ref.getContext, ref)
            newRef match {
              case refImpl: ScReferenceExpressionImpl =>
                refImpl.doResolve(refImpl, completionProcessor(refImpl, collectImplicit = true, postProcess))
              case _ =>
            }
          }

        }
        case _ =>
      }
      if (elementType == ScalaDocTokenType.DOC_TAG_VALUE_TOKEN) result.stopHere()
    }
  })

  override def advertise(parameters: CompletionParameters): String = {
    if (!parameters.getOriginalFile.isInstanceOf[ScalaFile]) return null
    val messages = Array[String](
      null
    )
    messages apply (new Random).nextInt(messages.size)
  }

  override def beforeCompletion(context: CompletionInitializationContext) {
    addedElements.clear()
    val offset = context.getStartOffset - 1
    val file = context.getFile
    val element = file.findElementAt(offset)
    val ref = file.findReferenceAt(offset)
    if (element != null && ref != null) {
      val text = ref match {
        case ref: PsiElement => ref.getText
        case ref: PsiReference => ref.getElement.getText //this case for anonymous method in ScAccessModifierImpl
      }
      val rest = ref match {
        case ref: PsiElement => text.substring(offset - ref.getTextRange.getStartOffset + 1)
        case ref: PsiReference => text.substring(offset - ref.getElement.getTextRange.getStartOffset + 1)
      }
      val id = if (isOpChar(text(text.length - 1))) {
        "+++++++++++++++++++++++"
      } else if (ScalaNamesUtil.isKeyword(rest)) {
        CompletionUtil.DUMMY_IDENTIFIER
      } else {
        CompletionUtil.DUMMY_IDENTIFIER_TRIMMED
      }
      context.setDummyIdentifier(
        if (ref.getElement != null &&
                ref.getElement.getPrevSibling != null &&
                ref.getElement.getPrevSibling.getNode.getElementType == ScalaTokenTypes.tSTUB) id + "`" else id
      )
    } else {
      if (element != null && element.getNode.getElementType == ScalaTokenTypes.tSTUB) {
        context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED + "`")
      } else {
        val actualElement = file.findElementAt(offset + 1)
        if (actualElement != null && ScalaNamesUtil.isKeyword(actualElement.getText)) {
          context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER)
        } else {
          context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
        }
      }
    }
    super.beforeCompletion(context)
  }

  private def isOpChar(c: Char): Boolean = {
    ScalaNamesUtil.isIdentifier("+" + c)
  }

  @Nullable
  private def getQualifierCastType(ref: ScReferenceElement, parameters: CompletionParameters): ScType = {
    ref match {
      case refExpr: ScReferenceExpression =>
        (for (qualifier <- refExpr.qualifier) yield {
          val evaluator = refExpr.getContainingFile.getCopyableUserData(ScalaRuntimeTypeEvaluator.KEY)
          if (evaluator != null) evaluator(qualifier) else null
        }).getOrElse(null)
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