package org.jetbrains.plugins.scala
package lang.completion.lookups

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementDecorator, LookupElementPresentation, LookupItem}
import com.intellij.openapi.util.Condition
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IconUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport
import org.jetbrains.plugins.scala.codeInspection.redundantBlock.RedundantBlockInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaInsertHandler
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings._

import scala.annotation.tailrec

/**
 * @author Alefas
 * @since 22.03.12
 */
class ScalaLookupItem(val element: PsiNamedElement, _name: String, containingClass0: Option[PsiClass] = None) extends {
  val name: String = if (ScalaNamesUtil.isKeyword(_name) && _name != "this") "`" + _name + "`" else _name
} with LookupItem[PsiNamedElement](element, name) {

  var isClassName: Boolean = false
  var isRenamed: Option[String] = None
  var isAssignment: Boolean = false
  var substitutor: ScSubstitutor = ScSubstitutor.empty
  var shouldImport: Boolean = false
  var isOverloadedForClassName: Boolean = false
  var isNamedParameter: Boolean = false
  var isDeprecated: Boolean = false
  var isUnderlined: Boolean = false
  var isInImport: Boolean = false
  var isInStableCodeReference: Boolean = false
  var usedImportStaticQuickfix: Boolean = false
  var elementToImport: PsiNamedElement = null
  var someSmartCompletion: Boolean = false
  var typeParametersProblem: Boolean = false
  var typeParameters: Seq[ScType] = Seq.empty
  var bold: Boolean = false
  var etaExpanded: Boolean = false
  var prefixCompletion: Boolean = false
  var isLocalVariable: Boolean = false
  var isSbtLookupItem: Boolean = false
  var isInSimpleString: Boolean = false
  var isInSimpleStringNoBraces: Boolean = false
  var isInInterpolatedString: Boolean = false

  def isNamedParameterOrAssignment = isNamedParameter || isAssignment

  val containingClass = containingClass0.getOrElse(ScalaPsiUtil.nameContext(element) match {
    case memb: PsiMember => memb.containingClass
    case _ => null
  })

  override def equals(o: Any): Boolean = {
    if (!super.equals(o)) return false
    o match {
      case s: ScalaLookupItem => if (isNamedParameter != s.isNamedParameter || containingClass != s.containingClass) return false
      case _ =>
    }
    true
  }

  override def renderElement(presentation: LookupElementPresentation) {
    val tailText: String = element match {
      case t: ScFun =>
        if (t.typeParameters.nonEmpty) t.typeParameters.map(param => presentationString(param, substitutor)).
          mkString("[", ", ", "]")
        else ""
      case t: ScTypeParametersOwner =>
        t.typeParametersClause match {
          case Some(tp) => presentationString(tp, substitutor)
          case None => ""
        }
      case p: PsiTypeParameterListOwner if p.getTypeParameters.nonEmpty =>
        p.getTypeParameters.map(ptp => presentationString(ptp)).mkString("[", ", ", "]")
      case p: PsiPackage => s"    (${p.getQualifiedName})"
      case _ => ""
    }
    element match {
      //scala
      case fun: ScFunction =>
        val scType = if (!etaExpanded) fun.returnType.getOrAny else fun.getType(TypingContext.empty).getOrAny
        presentation.setTypeText(presentationString(scType, substitutor))
        val tailText1 = if (isAssignment) {
          " = " + presentationString(fun.paramClauses, substitutor)
        } else {
          tailText + (
            if (!isOverloadedForClassName) presentationString(fun.paramClauses, substitutor)
            else "(...)"
            ) + (
            if (shouldImport && isClassName && containingClass != null)
              " " + containingClass.getPresentation.getLocationString
            else if (isClassName && containingClass != null)
              " in " + containingClass.name + " " + containingClass.getPresentation.getLocationString
            else ""
            )
        }
        if (!etaExpanded)
          presentation.setTailText(tailText1)
        else presentation.setTailText(" _")
      case fun: ScFun =>
        presentation.setTypeText(presentationString(fun.retType, substitutor))
        val paramClausesText = fun.paramClauses.map(_.map(presentationString(_, substitutor)).
          mkString("(", ", ", ")")).mkString
        presentation.setTailText(tailText + paramClausesText)
      case bind: ScBindingPattern =>
        presentation.setTypeText(presentationString(bind.getType(TypingContext.empty).getOrAny, substitutor))
      case f: ScFieldId =>
        presentation.setTypeText(presentationString(f.getType(TypingContext.empty).getOrAny, substitutor))
      case param: ScParameter =>
        val str: String =
          presentationString(param.getRealParameterType(TypingContext.empty).getOrAny, substitutor)
        if (isNamedParameter) {
          presentation.setTailText(" = " + str)
        } else {
          presentation.setTypeText(str)
        }
      case clazz: PsiClass =>
        val location: String = clazz.getPresentation.getLocationString
        presentation.setTailText(tailText + " " + location, true)
        if (name == "this" || name.endsWith(".this")) {
          clazz match {
            case t: ScTemplateDefinition =>
              t.getTypeWithProjections(TypingContext.empty, thisProjections = true) match {
                case Success(tp, _) =>
                  presentation.setTypeText(tp.presentableText)
                case _ =>
              }
            case _ =>
          }
        }
      case alias: ScTypeAliasDefinition =>
        presentation.setTypeText(presentationString(alias.aliasedType.getOrAny, substitutor))
      case method: PsiMethod =>
        val str: String = presentationString(method.getReturnType, substitutor)
        if (isNamedParameter) {
          presentation.setTailText(" = " + str)
        } else {
          presentation.setTypeText(str)
          val params =
            if (!isOverloadedForClassName) presentationString(method.getParameterList, substitutor)
            else "(...)"
          val tailText1 = tailText + params + (
            if (shouldImport && isClassName && containingClass != null)
              " " + containingClass.getPresentation.getLocationString
            else if (isClassName && containingClass != null)
              " in " + containingClass.name + " " + containingClass.getPresentation.getLocationString
            else ""
            )
          presentation.setTailText(tailText1)
        }
      case f: PsiField =>
        presentation.setTypeText(presentationString(f.getType, substitutor))
      case p: PsiPackage => presentation.setTailText(tailText, /*grayed*/ true)
      case _ =>
    }
    if (presentation.isReal)
      presentation.setIcon(element.getIcon(0))
    else presentation.setIcon(IconUtil.getEmptyIcon(false))
    var itemText: String =
      if (isRenamed.isEmpty) if (isClassName && shouldImport) {
        if (containingClass != null) containingClass.name + "." + name
        else name
      } else name
      else name + " <= " + element.name
    if (someSmartCompletion) itemText = "Some(" + itemText + ")"
    presentation.setItemText(itemText)
    presentation.setStrikeout(isDeprecated)
    presentation.setItemTextBold(bold)
    if (ScalaProjectSettings.getInstance(element.getProject).isShowImplisitConversions) {
      presentation.setItemTextUnderlined(isUnderlined)
    }
  }

  private def simpleInsert(context: InsertionContext) {
    new ScalaInsertHandler().handleInsert(context, this)
  }

  override def handleInsert(context: InsertionContext) {
    def shift: Int = {
      val smartAdd = if (someSmartCompletion) 5 else 0
      val simpleStringAdd =
        if (isInSimpleString && isInSimpleStringNoBraces) 1
        else if (isInSimpleString) 2
        else if (isInInterpolatedString) 1
        else 0
      smartAdd + simpleStringAdd
    }

    if (getInsertHandler != null) super.handleInsert(context)
    else if (isClassName || prefixCompletion) {
      context.commitDocument()
      element match {
        case TypeToImport(_) if isRenamed.isDefined => //do nothing
        case TypeToImport(cl) =>
          if (isInSimpleString) {
            val literal = context.getFile.findElementAt(context.getStartOffset).getParent
            val startOffset = context.getStartOffset
            val tailOffset = context.getTailOffset
            val literalOffset = literal.getTextRange.getStartOffset
            val document = context.getDocument
            document.insertString(tailOffset, "}")
            document.insertString(startOffset, "{")
            document.insertString(literalOffset, "s")
            context.commitDocument()
            val index = context.getStartOffset + 2
            val elem = context.getFile.findElementAt(index)
            elem.getNode.getElementType match {
              case ScalaTokenTypes.tIDENTIFIER =>
                val reference = elem.getParent
                reference.getParent match {
                  case block: ScBlock if RedundantBlockInspection.isRedundantBlock(block) =>
                    block.replace(reference)
                    isInSimpleStringNoBraces = true
                  case _ =>
                }
            }
          }
          var ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile, context.getStartOffset + shift, classOf[ScReferenceElement], false)
          val useFullyQualifiedName = PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) != null &&
            PsiTreeUtil.getParentOfType(ref, classOf[ScImportSelectors]) == null //do not complete in sel
          if (ref == null) return
          while (ref.getParent != null && ref.getParent.isInstanceOf[ScReferenceElement] &&
            (ref.getParent.asInstanceOf[ScReferenceElement].qualifier match {
              case Some(r) => r != ref
              case _ => true
            }))
            ref = ref.getParent.asInstanceOf[ScReferenceElement]
          val newRef = ref match {
            case ref: ScReferenceExpression if prefixCompletion =>
              val parts = cl.qualifiedName.split('.')
              if (parts.length > 1) {
                val newRefText = parts.takeRight(2).mkString(".")
                ScalaPsiElementFactory.createExpressionFromText(newRefText, ref.getManager).asInstanceOf[ScReferenceExpression]
              } else {
                ref.createReplacingElementWithClassName(useFullyQualifiedName, cl)
              }
            case ref: ScStableCodeReferenceElement if prefixCompletion =>
              val parts = cl.qualifiedName.split('.')
              if (parts.length > 1) {
                val newRefText = parts.takeRight(2).mkString(".")
                ScalaPsiElementFactory.createReferenceFromText(newRefText, ref.getManager)
              } else {
                ref.createReplacingElementWithClassName(useFullyQualifiedName, cl)
              }
            case _ =>
              ref.createReplacingElementWithClassName(useFullyQualifiedName, cl)
          }
          ref.getNode.getTreeParent.replaceChild(ref.getNode, newRef.getNode)
          newRef.bindToElement(cl.element)
          if (cl.element.isInstanceOf[ScObject] && isInStableCodeReference) {
            context.setLaterRunnable(new Runnable {
              def run() {
                AutoPopupController.getInstance(context.getProject).scheduleAutoPopup(
                  context.getEditor, new Condition[PsiFile] {
                    def value(t: PsiFile): Boolean = t == context.getFile
                  }
                )
              }
            })
          }
        case p: PsiPackage if shouldImport =>
          simpleInsert(context)
          context.commitDocument()
          val ref: ScReferenceElement =
            PsiTreeUtil.findElementOfClassAtOffset(context.getFile, context.getStartOffset + shift, classOf[ScReferenceElement], false)
          if (ref == null) return
          ScalaImportTypeFix.getImportHolder(ref, ref.getProject).addImportForPath(p.getQualifiedName)
        case _ =>
          simpleInsert(context)
          if (containingClass != null) {
            val document = context.getEditor.getDocument
            PsiDocumentManager.getInstance(context.getProject).commitDocument(document)
            context.getFile match {
              case scalaFile: ScalaFile =>
                val elem = scalaFile.findElementAt(context.getStartOffset + shift)
                def qualifyReference(ref: ScReferenceExpression) {
                  val newRef = ScalaPsiElementFactory.createExpressionFromText(
                    containingClass.name + "." + ref.getText,
                    containingClass.getManager).asInstanceOf[ScReferenceExpression]
                  ref.getNode.getTreeParent.replaceChild(ref.getNode, newRef.getNode)
                  newRef.qualifier.get.asInstanceOf[ScReferenceExpression].bindToElement(containingClass)
                }
                elem.getParent match {
                  case ref: ScReferenceExpression if !usedImportStaticQuickfix =>
                    if (shouldImport) qualifyReference(ref)
                  case ref: ScReferenceExpression =>
                    if (!shouldImport) qualifyReference(ref)
                    else {
                      if (elementToImport == null) {
                        //import static
                        ref.bindToElement(element, Some(containingClass))
                      } else {
                        ScalaPsiUtil.nameContext(elementToImport) match {
                          case member: PsiMember =>
                            val containingClass = member.containingClass
                            if (containingClass != null && containingClass.qualifiedName != null) {
                              ScalaImportTypeFix.getImportHolder(ref, ref.getProject).addImportForPsiNamedElement(elementToImport, null)
                            }
                          case _ =>
                        }
                      }
                    }
                  case _ =>
                }
              case _ =>
            }
          }
      }
    } else simpleInsert(context)
  }
}

object ScalaLookupItem {
  def unapply(item: ScalaLookupItem): Option[PsiNamedElement] = Some(item.element)

  @tailrec
  def original(element: LookupElement): LookupElement = element match {
    case decorator: LookupElementDecorator[_] => original(decorator.getDelegate)
    case it => it
  }
}
