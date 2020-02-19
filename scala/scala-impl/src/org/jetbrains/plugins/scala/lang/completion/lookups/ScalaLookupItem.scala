package org.jetbrains.plugins.scala
package lang
package completion
package lookups

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementDecorator, LookupElementPresentation, LookupItem}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaAddImportAction.getImportHolder
import org.jetbrains.plugins.scala.annotator.intention.{ClassToImport, ElementToImport, PrefixPackageToImport, TypeAliasToImport}
import org.jetbrains.plugins.scala.codeInspection.redundantBlock.RedundantBlockInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem.createReferenceToReplace
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction, ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.escapeKeyword
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.HashBuilder._
import org.jetbrains.plugins.scala.util.UIFreezingGuard

import scala.annotation.tailrec

/**
 * @author Alefas
 * @since 22.03.12
 */
final class ScalaLookupItem private(val element: PsiNamedElement,
                                    override val getLookupString: String,
                                    private val containingClass: PsiClass)
  extends LookupItem[PsiNamedElement](element, getLookupString) {

  private implicit val project: ProjectContext = element.projectContext

  def this(element: PsiNamedElement,
           name: String,
           maybeContainingClass: Option[PsiClass] = None) = this(
    element,
    name match {
      case ScalaKeyword.THIS => name
      case _ => escapeKeyword(name)
    },
    maybeContainingClass.orElse(element.containingClassOfNameContext).orNull
  )

  var isClassName: Boolean = false
  var isRenamed: Option[String] = None
  var isAssignment: Boolean = false
  var substitutor: ScSubstitutor = ScSubstitutor.empty
  var shouldImport: Boolean = false
  var isOverloadedForClassName: Boolean = false
  var isNamedParameter: Boolean = false
  var isUnderlined: Boolean = false
  var isInImport: Boolean = false
  var isInStableCodeReference: Boolean = false
  var usedImportStaticQuickfix: Boolean = false
  var elementToImport: Option[PsiNamedElement] = None
  var classToImport: Option[PsiClass] = None
  var someSmartCompletion: Boolean = false
  var bold: Boolean = false
  var etaExpanded: Boolean = false
  var prefixCompletion: Boolean = false
  var isLocalVariable: Boolean = false
  var isSbtLookupItem: Boolean = false
  var isInSimpleString: Boolean = false
  var isInSimpleStringNoBraces: Boolean = false
  var isInInterpolatedString: Boolean = false

  def isNamedParameterOrAssignment: Boolean = isNamedParameter || isAssignment

  override def equals(o: Any): Boolean = super.equals(o) &&
    (o match {
      case s: ScalaLookupItem => isNamedParameter == s.isNamedParameter && containingClass == s.containingClass
      case _ => true
    })

  override def hashCode(): Int =
    super.hashCode() #+ isNamedParameter #+ containingClass

  val containingClassName: String = containingClass match {
    case null => null
    case clazz => clazz.name
  }

  override def renderElement(presentation: LookupElementPresentation): Unit = {
    if (isNamedParameter) {
      presentation.setTailText(s" = $typeText")
    } else {
      val grayed = element match {
        case _: PsiPackage | _: PsiClass => true
        case _ => false
      }
      presentation.setTailText(tailText, grayed)
      presentation.setTypeText(typeText)
    }

    presentation.setIcon(element)

    var itemText: String =
      if (isRenamed.nonEmpty)
        s"$getLookupString <= ${element.name}"
      else if (isClassName && shouldImport && containingClassName != null)
        s"$containingClassName.$getLookupString"
      else getLookupString

    if (someSmartCompletion) itemText = "Some(" + itemText + ")"
    presentation.setItemText(itemText)

    presentation.setStrikeout(element)

    presentation.setItemTextBold(bold)
    if (ScalaProjectSettings.getInstance(element.getProject).isShowImplisitConversions) {
      presentation.setItemTextUnderlined(isUnderlined)
    }
  }

  private lazy val typeText: String = {
    UIFreezingGuard.withDefaultValue("") {
      implicit val tpc: TypePresentationContext = TypePresentationContext(element)
      element match {
        case fun: ScFunction =>
          val scType = if (!etaExpanded) fun.returnType.getOrAny else fun.`type`().getOrAny
          presentationString(scType, substitutor)
        case fun: ScFun =>
          presentationString(fun.retType, substitutor)
        case alias: ScTypeAliasDefinition =>
          presentationString(alias.aliasedType.getOrAny, substitutor)
        case param: ScParameter =>
          presentationString(param.getRealParameterType.getOrAny, substitutor)
        case t: ScTemplateDefinition if getLookupString == "this" || getLookupString.endsWith(".this") =>
          t.getTypeWithProjections(thisProjections = true) match {
            case Right(tp) =>
              tp.presentableText(element)
            case _ => ""
          }
        case f: PsiField =>
          presentationString(f.getType, substitutor)
        case m: PsiMethod =>
          presentationString(m.getReturnType, substitutor)
        case t: Typeable =>
          presentationString(t.`type`().getOrAny, substitutor)
        case _ => ""
      }
    }
  }

  private lazy val tailText: String = {
    UIFreezingGuard.withDefaultValue("") {
      implicit val tpc: TypePresentationContext = TypePresentationContext(element)
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

  private def withTypeParamsText(implicit tpc: TypePresentationContext): String = element match {
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

  private def textForMethod(m: PsiMethod)(implicit tpc: TypePresentationContext): String = {
    val params = m match {
      case fun: ScFunction => fun.paramClauses
      case _ => m.getParameterList
    }
    val paramsText =
      if (!isOverloadedForClassName) presentationString(params, substitutor)
      else "(...)"
    val containingClassText: String = {
      if (isClassName && containingClassName != null) {
        s"${if (shouldImport) "" else " in " + containingClassName} ${containingClass.getPresentation.getLocationString}"
      } else ""
    }
    withTypeParamsText + paramsText + containingClassText
  }

  private def simpleInsert(context: InsertionContext): Unit = {
    new ScalaInsertHandler().handleInsert(context, this)
  }

  override def handleInsert(context: InsertionContext): Unit = {
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

      val maybeTypeToImport = element match {
        case clazz: PsiClass => Some(ClassToImport(clazz))
        case ta: ScTypeAlias => Some(TypeAliasToImport(ta))
        case pack: ScPackage => Some(PrefixPackageToImport(pack))
        case _ => None
      }

      maybeTypeToImport match {
        case Some(cl) if isRenamed.isEmpty =>
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
          var ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile, context.getStartOffset + shift, classOf[ScReference], false)
          val useFullyQualifiedName = PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) != null &&
            PsiTreeUtil.getParentOfType(ref, classOf[ScImportSelectors]) == null //do not complete in sel
          if (ref == null) return
          while (ref.getParent != null && ref.getParent.isInstanceOf[ScReference] &&
            (ref.getParent.asInstanceOf[ScReference].qualifier match {
              case Some(r) => r != ref
              case _ => true
            }))
            ref = ref.getParent.asInstanceOf[ScReference]

          val newRef = ref match {
            case ref: ScReferenceExpression if prefixCompletion =>
              val parts = cl.qualifiedName.split('.')
              if (parts.length > 1) {
                val newRefText = parts.takeRight(2).mkString(".")
                createExpressionFromText(newRefText).asInstanceOf[ScReferenceExpression]
              } else {
                createReferenceToReplace(ref, cl, useFullyQualifiedName)
              }
            case ref: ScStableCodeReference if prefixCompletion =>
              val parts = cl.qualifiedName.split('.')
              if (parts.length > 1) {
                val newRefText = parts.takeRight(2).mkString(".")
                createReferenceFromText(newRefText)
              } else {
                createReferenceToReplace(ref, cl, useFullyQualifiedName)
              }
            case _ =>
              createReferenceToReplace(ref, cl, useFullyQualifiedName)
          }
          ref.getNode.getTreeParent.replaceChild(ref.getNode, newRef.getNode)
          newRef.bindToElement(element)
          element match {
            case _: ScObject if isInStableCodeReference =>
              context.scheduleAutoPopup()
            case _ =>
          }
        case None =>
          element match {
            case p: PsiPackage if shouldImport =>
              simpleInsert(context)
              context.commitDocument()
              val ref: ScReference =
                PsiTreeUtil.findElementOfClassAtOffset(context.getFile, context.getStartOffset + shift, classOf[ScReference], false)
              if (ref == null) return
              getImportHolder(ref, ref.getProject).addImportForPath(p.getQualifiedName)
            case _ =>
              simpleInsert(context)
              if (containingClass != null) {
                val document = context.getEditor.getDocument
                PsiDocumentManager.getInstance(context.getProject).commitDocument(document)
                context.getFile match {
                  case scalaFile: ScalaFile =>
                    val elem = scalaFile.findElementAt(context.getStartOffset + shift)

                    def qualifyReference(ref: ScReferenceExpression): Unit = {
                      val newRef = createExpressionFromText(s"$containingClassName.${ref.getText}")(containingClass.getManager)
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
                            case Some(named@ScalaPsiUtil.inNameContext(ContainingClass(clazz))) =>
                              if (clazz.qualifiedName != null) {
                                getImportHolder(ref, ref.getProject).addImportForPsiNamedElement(named, null, classToImport)
                              }
                          }
                        }
                      case _ =>
                    }
                  case _ =>
                }
              }
          }
        case _ =>
      }
    } else simpleInsert(context)
  }
}

object ScalaLookupItem {

  @tailrec
  def delegate(element: LookupElement): LookupElement = element match {
    case decorator: LookupElementDecorator[_] => delegate(decorator.getDelegate)
    case _ => element
  }

  def unapply(element: LookupElement): Option[(ScalaLookupItem, PsiNamedElement)] =
    delegate(element) match {
      case item: ScalaLookupItem => Some(item, item.element)
      case _ => None
    }

  def createReferenceToReplace(ref: ScReference, toImport: ElementToImport, useFQN: Boolean): ScReference = {
    val refText =
      if (ref.isInstanceOf[ScDocResolvableCodeReference] || useFQN) toImport.qualifiedName
      else toImport.name

    import ref.projectContext

    ref match {
      case expr: ScReferenceExpression => createExpressionFromText(refText).asInstanceOf[ScReferenceExpression]
      case _ => createReferenceFromText(refText)
    }

  }
}
