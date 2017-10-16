package org.jetbrains.plugins.scala
package lang.completion.lookups

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.{CompletionType, InsertionContext}
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
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, Typeable}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.escapeKeyword
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.UIFreezingGuard

import scala.annotation.tailrec

/**
 * @author Alefas
 * @since 22.03.12
 */
class ScalaLookupItem(val element: PsiNamedElement, _name: String, containingClass0: Option[PsiClass] = None) extends {
  val name: String = if (_name != "this") escapeKeyword(_name) else _name
} with LookupItem[PsiNamedElement](element, name) {

  private implicit val project: ProjectContext = element.projectContext

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
  var elementToImport: Option[PsiNamedElement] = None
  var objectOfElementToImport: Option[ScObject] = None
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

  def isNamedParameterOrAssignment: Boolean = isNamedParameter || isAssignment

  val containingClass: PsiClass = containingClass0.getOrElse(ScalaPsiUtil.nameContext(element) match {
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
    if (isNamedParameter) {
      presentation.setTailText(s" = $typeText")
    } else {
      val greyed = element match {
        case _: PsiPackage | _: PsiClass => true
        case _ => false
      }
      presentation.setTailText(tailText, greyed)
      presentation.setTypeText(typeText)
    }
    if (presentation.isReal) presentation.setIcon(element.getIcon(0))
    else presentation.setIcon(IconUtil.getEmptyIcon(false))

    var itemText: String =
      if (isRenamed.nonEmpty)
        s"$name <= ${element.name}"
      else if (isClassName && shouldImport && containingClass != null)
        s"${containingClass.name}.$name"
      else name

    if (someSmartCompletion) itemText = "Some(" + itemText + ")"

    presentation.setItemText(itemText)
    presentation.setStrikeout(isDeprecated)
    presentation.setItemTextBold(bold)
    if (ScalaProjectSettings.getInstance(element.getProject).isShowImplisitConversions) {
      presentation.setItemTextUnderlined(isUnderlined)
    }
  }

  private lazy val typeText: String = {
    UIFreezingGuard.withDefaultValue("") {
      element match {
        case fun: ScFunction =>
          val scType = if (!etaExpanded) fun.returnType.getOrAny else fun.getType().getOrAny
          presentationString(scType, substitutor)
        case fun: ScFun =>
          presentationString(fun.retType, substitutor)
        case alias: ScTypeAliasDefinition =>
          presentationString(alias.aliasedType.getOrAny, substitutor)
        case param: ScParameter =>
          presentationString(param.getRealParameterType.getOrAny, substitutor)
        case t: ScTemplateDefinition if name == "this" || name.endsWith(".this") =>
          t.getTypeWithProjections(thisProjections = true) match {
            case Success(tp, _) =>
              tp.presentableText
            case _ => ""
          }
        case f: PsiField =>
          presentationString(f.getType, substitutor)
        case m: PsiMethod =>
          presentationString(m.getReturnType, substitutor)
        case t: Typeable =>
          presentationString(t.getType().getOrAny, substitutor)
        case _ => ""
      }
    }
  }

  private lazy val tailText: String = {
    UIFreezingGuard.withDefaultValue("") {
      element match {
        //scala
        case fun: ScFunction =>
          if (etaExpanded) " _"
          else if (isAssignment) " = " + presentationString(fun.paramClauses, substitutor)
          else textForMethod(fun)
        case fun: ScFun =>
          val paramClausesText = fun.paramClauses.map(_.map(presentationString(_, substitutor)).mkString("(", ", ", ")")).mkString
          withTypeParamsText + paramClausesText
        case clazz: PsiClass =>
          val location: String = clazz.getPresentation.getLocationString
          s"$withTypeParamsText $location"
        case method: PsiMethod =>
          textForMethod(method)
        case p: PsiPackage =>
          s"    (${p.getQualifiedName})"
        case _ => ""
      }
    }
  }

  private def withTypeParamsText: String = element match {
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
    case _ => ""
  }

  private def textForMethod(m: PsiMethod): String = {
    val params = m match {
      case fun: ScFunction => fun.paramClauses
      case _ => m.getParameterList
    }
    val paramsText =
      if (!isOverloadedForClassName) presentationString(params, substitutor)
      else "(...)"
    val containingClassText: String = {
      if (isClassName && containingClass != null) {
        if (shouldImport) " " + containingClass.getPresentation.getLocationString
        else " in " + containingClass.name + " " + containingClass.getPresentation.getLocationString
      } else ""
    }
    withTypeParamsText + paramsText + containingClassText
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
                createExpressionFromText(newRefText).asInstanceOf[ScReferenceExpression]
              } else {
                ref.createReplacingElementWithClassName(useFullyQualifiedName, cl)
              }
            case ref: ScStableCodeReferenceElement if prefixCompletion =>
              val parts = cl.qualifiedName.split('.')
              if (parts.length > 1) {
                val newRefText = parts.takeRight(2).mkString(".")
                createReferenceFromText(newRefText)
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
                  context.getEditor, CompletionType.BASIC, new Condition[PsiFile] {
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
                  val newRef = createExpressionFromText(s"${containingClass.name}.${ref.getText}")(containingClass.getManager)
                    .asInstanceOf[ScReferenceExpression]
                  ref.getNode.getTreeParent.replaceChild(ref.getNode, newRef.getNode)
                  newRef.qualifier.get.asInstanceOf[ScReferenceExpression].bindToElement(containingClass)
                }
                elem.getParent match {
                  case ref: ScReferenceExpression if !usedImportStaticQuickfix =>
                    if (shouldImport) qualifyReference(ref)
                  case ref: ScReferenceExpression =>
                    if (!shouldImport) qualifyReference(ref)
                    else {
                      elementToImport match {
                        case None => ref.bindToElement(element, Some(containingClass))
                        case Some(named @ ScalaPsiUtil.inNameContext(ContainingClass(clazz))) =>
                          if (clazz.qualifiedName != null) {
                            ScalaImportTypeFix.getImportHolder(ref, ref.getProject).addImportForPsiNamedElement(named, null, objectOfElementToImport)
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
