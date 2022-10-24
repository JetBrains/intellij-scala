package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.editor.EditorExt
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScRefinement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}

import scala.collection.mutable

class AddUnitFunctionSignatureEnterHandler extends EnterHandlerDelegateAdapter {
  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!isApplicable(file, editor)) return Result.Continue

    val document = editor.getDocument
    val project = file.getProject
    editor.commitDocument(project)

    val element = file.findElementAt(editor.offset)
    if (element == null) return Result.Continue

    @inline def checkBlock2(blockLike: ScalaPsiElement): Boolean =
      blockLike.getNode.getChildren(null) match {
        case Array(ElementType(ScalaTokenTypes.tLBRACE), ws: PsiWhiteSpace, ElementType(ScalaTokenTypes.tRBRACE)) =>
          ws.getText.count(_ == '\n') == 2
        case _ => false
      }

    /**
     * Add empty parentheses, Unit type annotation and `=` if needed.
     *
     * @param fn     function whose signature may need to be fixed
     * @param anchor function body start ([[ScBlockExpr]] or [[ScRefinement]])
     */
    def fixSignature(fn: ScFunction, anchor: ScalaPsiElement): Unit = {
      val settings = ScalaCodeStyleSettings.getInstance(project)

      val addEmptyParens = fn.isParameterless
      val addType = settings.TYPE_ANNOTATION_UNIT_TYPE && fn.returnTypeElement.isEmpty
      val addAssign =
        !fn.hasAssign &&
          // since Scala 3.0 procedure syntax is not supported
          (settings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT || fn.isInScala3File)

      if (!addType && !addAssign) {
        if (addEmptyParens) {
          // Only add empty parens
          inWriteAction {
            document.insertString(fn.paramClauses.getTextOffset, "()")
          }
        }
      } else {
        // Maybe add empty parens
        // Add type annotation and/or `=`
        // Simplest way is to replace string in editor from param clauses to the anchor
        val textBuilder = new mutable.StringBuilder()

        val startOffset = if (addEmptyParens) {
          textBuilder.append("()")
          fn.paramClauses.getTextOffset
        } else fn.paramClauses.endOffset

        textBuilder.append(": Unit = ")

        inWriteAction {
          document.replaceString(startOffset, anchor.startOffset, textBuilder.result())
        }
      }
    }

    element.getParent match {
      case block: ScBlockExpr if checkBlock2(block) =>
        block.getParent match {
          case fn: ScFunctionDefinition =>
            val hasAssign = fn.hasAssign
            val returnTypeElement = fn.returnTypeElement

            if (hasAssign) {
              // if there is `=` and no explicit type, we cannot be sure that that return type will be Unit
              if (returnTypeElement.exists(isUnit))
                fixSignature(fn, block)
            } else if (returnTypeElement.forall(isUnit))
              fixSignature(fn, block)
          case _ =>
        }

      /*
      * When there is an explicit return type but no `=`
      * E.g.: `def foo: Unit {<caret>}`
      */
      case refinement: ScRefinement if checkBlock2(refinement) =>
        refinement.getParent match {
          case compoundTpe@ScCompoundTypeElement(Seq(tpe: ScSimpleTypeElement), _) if isUnit(tpe) =>
            compoundTpe.getParent match {
              case fn: ScFunctionDeclaration =>
                fixSignature(fn, refinement)
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }

    Result.Default
  }

  private def isUnit(typeElement: ScTypeElement): Boolean =
    typeElement.`type`().exists(_.isUnit)

  private def isApplicable(file: PsiFile, editor: Editor): Boolean =
    file.is[ScalaFile] && prevNonWhitespace(editor) == '{'

  private def prevNonWhitespace(editor: Editor): Char = {
    val chars = editor.getDocument.getImmutableCharSequence
    var offset = editor.offset
    var found = ' '
    while (found.isWhitespace && offset > 0) {
      offset -= 1
      found = chars.charAt(offset)
    }
    found
  }
}
