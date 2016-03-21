package org.jetbrains.plugins.scala.lang.completion.handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Condition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaConstructorInsertHandler extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement) {
    val editor = context.getEditor
    val document = editor.getDocument
    if (context.getCompletionChar == '(') {
      context.setAddCompletionChar(false)
    } else if (context.getCompletionChar == '[') {
      context.setAddCompletionChar(false)
    }
    val startOffset = context.getStartOffset
    val lookupStringLength = item.getLookupString.length
    var endOffset = startOffset + lookupStringLength

    item match {
      case ScalaLookupItem(obj: ScObject) =>
        if (context.getCompletionChar != '.') {
          document.insertString(endOffset, ".")
          endOffset += 1
          editor.getCaretModel.moveToOffset(endOffset)
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
      case item@ScalaLookupItem(clazz: PsiClass) =>
        val isRenamed = item.isRenamed.isDefined
        var hasNonEmptyParams = false
        clazz match {
          case c: ScClass =>
            c.constructor match {
              case Some(constr) if constr.parameters.nonEmpty => hasNonEmptyParams = true
              case _ =>
            }
            c.secondaryConstructors.foreach(fun => if (fun.parameters.nonEmpty) hasNonEmptyParams = true)
          case _ =>
            clazz.getConstructors.foreach(meth => if (meth.getParameterList.getParametersCount > 0) hasNonEmptyParams = true)
        }
        if (context.getCompletionChar == '(') hasNonEmptyParams = true
        if (item.typeParametersProblem) {
          document.insertString(endOffset, "[]")
          endOffset += 2
          editor.getCaretModel.moveToOffset(endOffset - 1)
        } else if (item.typeParameters.nonEmpty) {
          val str = item.typeParameters.map(_.canonicalText).mkString("[", ", ", "]")
          document.insertString(endOffset, str)
          endOffset += str.length()
          editor.getCaretModel.moveToOffset(endOffset)
        }
        if (hasNonEmptyParams) {
          document.insertString(endOffset, "()")
          endOffset += 2
          if (!item.typeParametersProblem)
            editor.getCaretModel.moveToOffset(endOffset - 1)
        }

        if (clazz.isInterface || clazz.isInstanceOf[ScTrait] ||
          clazz.hasModifierPropertyScala("abstract")) {
          document.insertString(endOffset, " {}")
          endOffset += 3
          if (!item.typeParametersProblem)
            editor.getCaretModel.moveToOffset(endOffset - 1)
        }
        PsiDocumentManager.getInstance(context.getProject).commitDocument(document)
        val file = context.getFile
        val element = file.findElementAt(endOffset - 1)
        val newT = PsiTreeUtil.getParentOfType(element, classOf[ScNewTemplateDefinition])
        if (newT != null) {
          newT.extendsBlock.templateParents match {
            case Some(tp: ScTemplateParents) =>
              val elements = tp.typeElements
              if (elements.length == 1) {
                val element: ScTypeElement = elements.head
                val ref: ScStableCodeReferenceElement = element match {
                  case simple: ScSimpleTypeElement => simple.reference.orNull
                  case par: ScParameterizedTypeElement => par.typeElement match {
                    case simple: ScSimpleTypeElement => simple.reference.orNull
                    case _ => null
                  }
                  case _ => null
                }
                if (ref != null && !isRenamed) {
                  if (item.prefixCompletion) {
                    val newRefText = clazz.qualifiedName.split('.').takeRight(2).mkString(".")
                    val newRef = ScalaPsiElementFactory.createReferenceFromText(newRefText, clazz.getManager)
                    val replaced = ref.replace(newRef).asInstanceOf[ScStableCodeReferenceElement]
                    replaced.bindToElement(clazz)
                  } else {
                    ref.bindToElement(clazz)
                  }
                }
              }
            case _ =>
          }
          ScalaPsiUtil.adjustTypes(newT)
        }

        if ((clazz.isInterface || clazz.isInstanceOf[ScTrait] ||
          clazz.hasModifierPropertyScala("abstract")) && !item.typeParametersProblem) {
          context.setLaterRunnable(new Runnable {
            def run() {
              val file = context.getFile
              val element = file.findElementAt(editor.getCaretModel.getOffset)
              if (element == null) return

              element.getParent match {
                case (_: ScTemplateBody) childOf ((_: ScExtendsBlock) childOf (newTemplateDef: ScNewTemplateDefinition)) =>
                  val members = ScalaOIUtil.getMembersToImplement(newTemplateDef)
                  ScalaOIUtil.runAction(members.toSeq, isImplement = true, newTemplateDef, editor)
                case _ => 
              }
            }
          })
        }
      case _ =>
    }
  }
}