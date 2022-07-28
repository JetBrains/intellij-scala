package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil.{getMembersToImplement, runAction}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

final class ScalaConstructorInsertHandler(typeParametersEvaluator: (ScType => String) => String,
                                          hasSubstitutionProblem: Boolean,
                                          isInterface: Boolean,
                                          isRenamed: Boolean,
                                          isPrefixCompletion: Boolean) extends InsertHandler[LookupElement] {

  override def handleInsert(context: InsertionContext,
                            element: LookupElement): Unit = {
    val InsertionContextExt(editor, document, file, project) = context

    if (context.getCompletionChar == '(') {
      context.setAddCompletionChar(false)
    } else if (context.getCompletionChar == '[') {
      context.setAddCompletionChar(false)
    }
    val startOffset = context.getStartOffset
    val lookupStringLength = element.getLookupString.length
    var endOffset = startOffset + lookupStringLength

    val model = editor.getCaretModel
    element.getPsiElement match {
      case _: ScObject =>
        if (context.getCompletionChar != '.') {
          document.insertString(endOffset, ".")
          endOffset += 1
          model.moveToOffset(endOffset)
          context.scheduleAutoPopup()
        }
      case clazz: PsiClass =>
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
        if (hasSubstitutionProblem) {
          document.insertString(endOffset, "[]")
          endOffset += 2
          model.moveToOffset(endOffset - 1)
        } else {
          val text = typeParametersEvaluator(_.canonicalText)
          document.insertString(endOffset, text)
          endOffset += text.length
          model.moveToOffset(endOffset)
        }
        if (hasNonEmptyParams) {
          document.insertString(endOffset, "()")
          endOffset += 2
          if (!hasSubstitutionProblem)
            model.moveToOffset(endOffset - 1)
        }

        if (isInterface) {
          onDefinition(file, startOffset) { newTemplateDef =>
            val (openBlock, closeBlock) = generateBlock(newTemplateDef)
            document.insertString(endOffset, openBlock)
            endOffset += openBlock.length
            if (!hasSubstitutionProblem)
              model.moveToOffset(endOffset)
            document.insertString(endOffset, closeBlock)
            endOffset += closeBlock.length
          }
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)

        onDefinition(file, endOffset - 1) {
          newTemplateDefinition =>
            newTemplateDefinition.extendsBlock.templateParents.toSeq.flatMap(_.typeElements) match {
              case Seq(ScSimpleTypeElement.unwrapped(reference)) if !isRenamed =>
                simplifyReference(clazz, reference).bindToElement(clazz)
              case _ =>
            }

            ScalaPsiUtil.adjustTypes(newTemplateDefinition)
        }

        if (isInterface && !hasSubstitutionProblem) {
          context.setLaterRunnable(() => {
            onDefinition(file, model.getOffset - 1) { newTemplateDefinition =>
              val members = getMembersToImplement(newTemplateDefinition)

              ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY =
                ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE

              runAction(
                members,
                isImplement = true,
                newTemplateDefinition
              )(project, editor)
            }
          })
        }
    }
  }

  private def simplifyReference(`class`: PsiClass,
                                reference: ScStableCodeReference): ScStableCodeReference =
    if (isPrefixCompletion) {
      // TODO unify with ScalaLookupItem
      val name = `class`.qualifiedName
        .split('.')
        .takeRight(2)
        .mkString(".")

      reference.replace {
        createReferenceFromText(name)(`class`)
      }.asInstanceOf[ScStableCodeReference]
    } else {
      reference
    }

  private def onDefinition(file: PsiFile, offset: Int)
                          (action: ScNewTemplateDefinition => Unit): Unit = {
    val element = file.findElementAt(offset) match {
      case e if e.isWhitespace => e.getPrevNonEmptyLeaf
      case e => e
    }

    getParentOfType(element, classOf[ScNewTemplateDefinition]) match {
      case null =>
      case newTemplateDefinition => action(newTemplateDefinition)
    }
  }

  private def generateBlock(newTemplateDefinition: ScNewTemplateDefinition): (String, String) = {
    val defaultBlock = (" {", "}")
    val file = newTemplateDefinition.containingFile.getOrElse(return defaultBlock)

    val useIndentationBasedSyntax = file.useIndentationBasedSyntax

    if (!useIndentationBasedSyntax || getMembersToImplement(newTemplateDefinition).isEmpty)
      defaultBlock
    else (":", "")
  }

}
