package org.jetbrains.plugins.scala
package editor.backspaceHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlStartTag
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.util.IndentUtil

/**
 * User: Dmitry Naydanov
 * Date: 2/24/12
 */

class ScalaBackspaceHandler extends BackspaceHandlerDelegate {

  override def beforeCharDeleted(c: Char, file: PsiFile, editor: Editor): Unit = {
    if (!file.isInstanceOf[ScalaFile]) return

    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset - 1)
    if (element == null) return

    if (needCorrectWiki(element)) {
      inWriteAction {
        val document = editor.getDocument

        if (element.getParent.getLastChild != element) {
          val tagToDelete = element.getParent.getLastChild

          if (ScaladocSyntaxElementType.canClose(element.getNode.getElementType, tagToDelete.getNode.getElementType)) {
            val textLength =
              if (tagToDelete.getNode.getElementType != ScalaDocTokenType.DOC_BOLD_TAG) tagToDelete.getTextLength else 1
            document.deleteString(tagToDelete.getTextOffset, tagToDelete.getTextOffset + textLength)
          }
        } else {
          document.deleteString(element.getTextOffset, element.getTextOffset + 2)
          editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
        }

        PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
      }
    } else if (element.getNode.getElementType == ScalaXmlTokenTypes.XML_NAME && element.getParent != null && element.getParent.isInstanceOf[ScXmlStartTag]) {
      val openingTag = element.getParent.asInstanceOf[ScXmlStartTag]
      val closingTag = openingTag.getClosingTag

      if (closingTag != null && closingTag.getTextLength > 3 && closingTag.getText.substring(2, closingTag.getTextLength - 1) == openingTag.getTagName) {
        inWriteAction {
          val offsetInName = editor.getCaretModel.getOffset - element.getTextOffset + 1
          editor.getDocument.deleteString(closingTag.getTextOffset + offsetInName, closingTag.getTextOffset + offsetInName + 1)
          PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
        }
      }
    } else if (element.getNode.getElementType == ScalaTokenTypes.tMULTILINE_STRING && offset - element.getTextOffset == 3) {
      correctMultilineString(file, editor, element.getTextOffset + element.getTextLength - 3)
    } else if (element.getNode.getElementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER && element.getNextSibling != null &&
      element.getNextSibling.getNode.getElementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        inWriteAction {
          editor.getDocument.deleteString(element.getTextOffset + 1, element.getTextOffset + 2)
          PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
        }
    } else if (offset - element.getTextOffset == 3 &&
            element.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING &&
            element.getParent.getLastChild.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_STRING_END &&
            element.getPrevSibling != null &&
            isMultilineInterpolatedStringPrefix(element.getPrevSibling.getNode.getElementType)) {
      correctMultilineString(file, editor, element.getParent.getLastChild.getTextOffset)
    } else if(c == '{' && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleLeftBrace(offset, element, file, editor)
    }
  }

  @inline
  private def isMultilineInterpolatedStringPrefix(tpe: IElementType) =
    Set(
      ScalaElementType.INTERPOLATED_PREFIX_LITERAL_REFERENCE,
      ScalaElementType.INTERPOLATED_PREFIX_PATTERN_REFERENCE,
      ScalaTokenTypes.tINTERPOLATED_STRING_ID
    ).contains(tpe)

  private def correctMultilineString(file: PsiFile, editor: Editor, closingQuotesOffset: Int): Unit = {
    inWriteAction {
      editor.getDocument.deleteString(closingQuotesOffset, closingQuotesOffset + 3)
      //editor.getCaretModel.moveCaretRelatively(-1, 0, false, false, false) //https://youtrack.jetbrains.com/issue/SCL-6490
      PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
    }
  }

  private def needCorrectWiki(element: PsiElement): Boolean = {
    (element.getNode.getElementType.isInstanceOf[ScaladocSyntaxElementType] || element.getText == "{{{") &&
      (element.getParent.getLastChild != element ||
        element.getText == "'''" && element.getPrevSibling != null &&
          element.getPrevSibling.getText == "'")
  }

  private def handleLeftBrace(offset: Int, element: PsiElement, file: PsiFile, editor: Editor): Unit = {
    val assignElement: PsiElement = {
      PsiTreeUtil.prevLeaf(element) match {
        case ws: PsiWhiteSpace => PsiTreeUtil.prevLeaf(ws)
        case prev => prev
      }
    }

    if (assignElement != null && assignElement.getNode.getElementType == ScalaTokenTypes.tASSIGN) {
      val definition = assignElement.getParent
      val bodyOpt: Option[ScExpression] = definition match {
        case patDef: ScPatternDefinition if patDef.bindings.size == 1 => patDef.expr
        case varDef: ScVariableDefinition => varDef.expr
        case funDef: ScFunctionDefinition => funDef.body
        case _ => None
      }

      bodyOpt match {
        case Some(block: ScBlockExpr) if block.statements.size == 1 =>
          block.getRBrace match {
            case Some(rBrace: ASTNode) =>
              val project = file.getProject
              val settings = CodeStyle.getSettings(project)
              val tabSize = settings.getTabSize(ScalaFileType.INSTANCE)
              if(IndentUtil.compare(rBrace.getPsi, definition, tabSize) >= 0) {
                val start = rBrace.getTreePrev match {
                  case ws if ws.getElementType == TokenType.WHITE_SPACE => ws.getStartOffset
                  case _ => rBrace.getStartOffset
                }
                val end = rBrace.getStartOffset + 1
                val document = editor.getDocument
                document.deleteString(start, end)
                document.commit(project)
              }
            case _ =>
          }
        case _ =>
      }
    }
  }

  /*
    In some cases with nested braces (like '{()}' ) IDEA can't properly handle backspace action due to 
    bag in BraceMatchingUtil (it looks for every lbrace/rbrace token regardless of they are a pair or not)
    So we have to fix it in our handler
   */
  override def charDeleted(charRemoved: Char, file: PsiFile, editor: Editor): Boolean = {
    val document = editor.getDocument
    val offset = editor.getCaretModel.getOffset

    if (!CodeInsightSettings.getInstance.AUTOINSERT_PAIR_BRACKET || offset >= document.getTextLength) return false

    def hasLeft: Option[Boolean] = {
      val fileType = file.getFileType
      if (fileType != ScalaFileType.INSTANCE) return None
      val txt = document.getImmutableCharSequence

      val iterator = editor.asInstanceOf[EditorEx].getHighlighter.createIterator(offset)
      val tpe = iterator.getTokenType
      if (tpe == null) return None

      val matcher = BraceMatchingUtil.getBraceMatcher(fileType, tpe)
      if (matcher == null) return None

      val stack = scala.collection.mutable.Stack[IElementType]()

      iterator.retreat()
      while (!iterator.atEnd() && iterator.getStart > 0 && iterator.getTokenType != null) {
        if (matcher.isRBraceToken(iterator, txt, fileType)) stack push iterator.getTokenType
          else if (matcher.isLBraceToken(iterator, txt, fileType)) {
          if (stack.isEmpty || !matcher.isPairBraces(iterator.getTokenType, stack.pop())) return Some(false)
        }

        iterator.retreat()
      }

      if (stack.isEmpty) Some(true)
      else None
    }

    @inline
    def fixBrace(): Unit = if (hasLeft.exists(!_)) {
      inWriteAction {
        document.deleteString(offset, offset + 1)
      }
    }

    val charNext = document.getImmutableCharSequence.charAt(offset)
    (charRemoved, charNext) match {
      case ('{', '}') => fixBrace()
      case ('(', ')') => fixBrace()
      case _ => 
    }
    
    false
  }

}
