package org.jetbrains.plugins.scala.lang.completion.handlers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.completion.{InsertionContext, InsertHandler}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScalaLookupObject
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScClass}
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import com.intellij.codeInsight.generation.ClassMember
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateParents, ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScParenthesisedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.util.Condition
import com.intellij.psi.{PsiFile, PsiDocumentManager, PsiClass}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaConstuctorInsertHandler extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement) {
    val editor = context.getEditor
    val document = editor.getDocument
    if (context.getCompletionChar == '(') {
      context.setAddCompletionChar(false)
    }
    val startOffset = context.getStartOffset
    val lookupStringLength = item.getLookupString.length
    var endOffset = startOffset + lookupStringLength

    val patchedObject = ScalaCompletionUtil.getScalaLookupObject(item)
    if (patchedObject == null) return

    patchedObject match {
      case ScalaLookupObject(obj: ScObject, _, _, _) => {
        document.insertString(endOffset, ".")
        endOffset += 1
        editor.getCaretModel.moveToOffset(endOffset)
        return
      }
      case obj@ScalaLookupObject(clazz: PsiClass, _, _, _) => {
        var hasNonEmptyParams = false
        clazz match {
          case c: ScClass =>
            c.constructor match {
              case Some(constr) if constr.parameters.length > 0 => hasNonEmptyParams = true
              case _ =>
            }
            c.secondaryConstructors.foreach(fun => if (fun.parameters.length > 0) hasNonEmptyParams = true)
          case _ =>
            clazz.getConstructors.foreach(meth => if (meth.getParameterList.getParametersCount > 0) hasNonEmptyParams = true)
        }
        if (context.getCompletionChar == '(') hasNonEmptyParams = true
        if (obj.typeParametersProblem) {
          document.insertString(endOffset, "[]")
          endOffset += 2
          editor.getCaretModel.moveToOffset(endOffset - 1)
        } else if (obj.getTypeParameters.length > 0) {
          val str = obj.getTypeParameters.map(ScType.canonicalText(_)).mkString("[", ", ", "]")
          document.insertString(endOffset, str)
          endOffset += str.length()
          editor.getCaretModel.moveToOffset(endOffset)
        }
        if (hasNonEmptyParams) {
          document.insertString(endOffset, "()")
          endOffset += 2
          if (!obj.typeParametersProblem)
            editor.getCaretModel.moveToOffset(endOffset - 1)
        }



        if (clazz.isInterface || clazz.isInstanceOf[ScTrait] ||
          clazz.hasModifierProperty("abstract")) {
          document.insertString(endOffset, " {}")
          endOffset += 3
          if (!obj.typeParametersProblem)
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
                val element: ScTypeElement = elements(0)
                val ref: ScStableCodeReferenceElement = element match {
                  case simple: ScSimpleTypeElement => simple.reference match {
                    case Some(ref) => ref
                    case _ => null
                  }
                  case par: ScParameterizedTypeElement => par.typeElement match {
                    case simple: ScSimpleTypeElement => simple.reference match {
                      case Some(ref) => ref
                      case _ => null
                    }
                    case _ => null
                  }
                  case _ => null
                }
                if (ref != null) {
                  ref.bindToElement(clazz)
                }
              }
            case _ =>
          }
          ScalaPsiUtil.adjustTypes(newT)
        }

        if ((clazz.isInterface || clazz.isInstanceOf[ScTrait] ||
          clazz.hasModifierProperty("abstract")) && !obj.typeParametersProblem) {
          context.setLaterRunnable(new Runnable {
            def run() {
              val file = context.getFile
              val element = file.findElementAt(editor.getCaretModel.getOffset)
              val parent = element.getParent
              if (!parent.isInstanceOf[ScTemplateBody]) return
              val extendsBlock = parent.getParent
              if (!extendsBlock.isInstanceOf[ScExtendsBlock]) return
              if (!extendsBlock.getParent.isInstanceOf[ScNewTemplateDefinition]) return
              val newTemplateDef = extendsBlock.getParent.asInstanceOf[ScNewTemplateDefinition]
              val members: Array[ClassMember] = ScalaOIUtil.toMembers(ScalaOIUtil.getMembersToImplement(newTemplateDef))
              val membersList = new java.util.ArrayList[ClassMember]
              for (member <- members) membersList.add(member)
              val b = ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY
              ScalaOIUtil.runAction(membersList, true, newTemplateDef, editor,
                if (b != null) b.booleanValue() else true)
            }
          })
        }
      }
      case _ =>
    }
  }
}