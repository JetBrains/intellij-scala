package org.jetbrains.plugins.scala.lang.completion.handlers

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.{InsertionContextExt, afterNewKeywordPattern}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScGenericCall, ScMethodCall, ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createReferenceExpressionFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil.{getMembersToImplement, runAction}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

private[completion]
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

    val isAfterNew = afterNewKeywordPattern.accepts(file.findElementAt(startOffset))

    val model = editor.getCaretModel
    element.getPsiElement match {
      case _: ScObject =>
        if (isAfterNew && context.getCompletionChar != '.') {
          document.insertString(endOffset, ".")
          endOffset += 1
          model.moveToOffset(endOffset)
          context.scheduleAutoPopup()
        }
      case clazz: PsiClass =>
        var needsEmptyParens = false
        clazz match {
          case c: ScClass =>
            c.constructor match {
              case Some(constr)
                if constr.parameters.nonEmpty ||
                  // universal apply
                  file.isScala3File && !isAfterNew =>
                needsEmptyParens = true
              case _ =>
            }
            c.secondaryConstructors.foreach(fun => if (fun.parameters.nonEmpty) needsEmptyParens = true)
          case enumCase: ScEnumCase =>
            if (enumCase.constructor.isDefined) needsEmptyParens = true
          case _ =>
            clazz.constructors.foreach(meth => if (meth.getParameterList.getParametersCount > 0) needsEmptyParens = true)
        }
        if (context.getCompletionChar == '(') needsEmptyParens = true
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
        if (needsEmptyParens) {
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

        if (isAfterNew) {
          onDefinition(file, startOffset) { newTemplateDefinition =>
            newTemplateDefinition.extendsBlock.templateParents.toSeq.flatMap(_.typeElements) match {
              case Seq(ScSimpleTypeElement.unwrapped(reference)) if !isRenamed =>
                simplifyReference(clazz, reference).bindToElement(clazz)
              case _ =>
            }

            ScalaPsiUtil.adjustTypes(newTemplateDefinition)
          }
        } else if (file.isScala3File) {
          val lookForCall = needsEmptyParens && !clazz.is[ScEnumCase]
          onCallOrReference(file, startOffset, lookForCall) { ref =>
            if (!isRenamed) simplifyReference(clazz, ref).bindToElement(clazz)
          }
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

  private def simplifyReference(`class`: PsiClass, reference: ScReference): ScReference = reference match {
    case ref: ScStableCodeReference =>
      doSimplifyReference(`class`, ref, createReferenceFromText(_)(`class`))
    case ref: ScReferenceExpression =>
      doSimplifyReference(`class`, ref, createReferenceExpressionFromText(_)(`class`))
    case _ => reference
  }

  private def doSimplifyReference[Ref <: ScReference](`class`: PsiClass,
                                                      reference: Ref,
                                                      createRef: String => Ref): Ref =
    if (isPrefixCompletion) {
      // TODO unify with ScalaLookupItem
      val name = `class`.qualifiedName
        .split('.')
        .takeRight(2)
        .mkString(".")

      val newRef = createRef(name)

      reference
        .replace(newRef)
        .asInstanceOf[Ref]
    } else {
      reference
    }

  private def onDefinition(file: PsiFile, offset: Int)
                          (action: ScNewTemplateDefinition => Unit): Unit =
    getParentOfType(findNonWhitespaceElement(file, offset), classOf[ScNewTemplateDefinition]) match {
      case null =>
      case newTemplateDefinition => action(newTemplateDefinition)
    }

  private def onCallOrReference(file: PsiFile, offset: Int, call: Boolean)
                               (action: ScReference => Unit): Unit = {
    val element = findNonWhitespaceElement(file, offset)
    val parent =
      if (call) getParentOfType(element, classOf[ScMethodCall], /* stop at: */ classOf[ScArgumentExprList])
      else getParentOfType(element, classOf[ScReference])

    parent match {
      case ref: ScReference => action(ref)
      case call: ScMethodCall =>
        call.getInvokedExpr match {
          case ref: ScReferenceExpression => action(ref)
          case ScGenericCall(ref, _) => action(ref)
          case _ =>
        }
      case _ =>
    }
  }

  @Nullable
  private def findNonWhitespaceElement(file: PsiFile, offset: Int): PsiElement =
    file.findElementAt(offset) match {
      case e if e.isWhitespace => e.getPrevNonEmptyLeaf
      case e => e
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
