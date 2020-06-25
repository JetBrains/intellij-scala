package org.jetbrains.plugins.scala
package editor.backspaceHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor._
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler.BraceWrapInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlStartTag
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil
import org.jetbrains.plugins.scala.util.MultilineStringUtil.{MultilineQuotesLength => QuotesLength}

class ScalaBackspaceHandler extends BackspaceHandlerDelegate {

  override def beforeCharDeleted(c: Char, file: PsiFile, editor: Editor): Unit = {
    if (!file.isInstanceOf[ScalaFile]) return

    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset - 1)
    if (element == null) return

    def scalaSettings = ScalaApplicationSettings.getInstance

    if (needCorrectWiki(element)) {
      inWriteAction {
        val document = editor.getDocument

        if (element.getParent.getLastChild != element) {
          val tagToDelete = element.getParent.getLastChild

          if (ScalaDocSyntaxElementType.canClose(element.getNode.getElementType, tagToDelete.getNode.getElementType)) {
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
    } else if (c == '"') {
      val hiterator = editor.asInstanceOf[EditorEx].getHighlighter.createIterator(offset)
      if (isInsideEmptyMultilineString(offset, hiterator)) {
        if (scalaSettings.INSERT_MULTILINE_QUOTES) {
          deleteMultilineStringClosingQuotes(editor, hiterator)
        }
      } else if (isInsideEmptyXmlAttributeValue(element)) {
        inWriteAction {
          val document = editor.getDocument
          document.deleteString(offset, offset + 1)
          editor.getDocument.commit(file.getProject)
        }
      }
    } else if (c == '{' && scalaSettings.WRAP_SINGLE_EXPRESSION_BODY) {
      handleLeftBrace(offset, element, file, editor)
    }
  }

  // TODO: simplify when parsing of incomplete multiline strings is unified for interpolated and non-interpolated strings
  //  see also ScalaQuoteHandler.startsWithMultilineQuotes
  private def isInsideEmptyMultilineString(offset: Int, hiterator: HighlighterIterator): Boolean = {
    import ScalaTokenTypes._
    hiterator.getTokenType match {
      case `tMULTILINE_STRING` =>
        hiterator.tokenLength == 2 * QuotesLength && offset == hiterator.getStart + QuotesLength
      case `tINTERPOLATED_STRING_END` =>
        hiterator.tokenLength == QuotesLength && offset == hiterator.getStart && {
          hiterator.retreat()
          try hiterator.getTokenType == tINTERPOLATED_MULTILINE_STRING && hiterator.tokenLength == QuotesLength
          finally hiterator.advance() // pretending we are side-affect-free =/
        }
      case _ =>
        false
    }
  }

  private def deleteMultilineStringClosingQuotes(editor: Editor, hiterator: HighlighterIterator): Unit = {
    import ScalaTokenTypes._
    val closingQuotesOffset = hiterator.getStart + (hiterator.getTokenType match {
      case `tMULTILINE_STRING`        => QuotesLength
      case `tINTERPOLATED_STRING_END` => 0
      case _                          => 0
    })
    inWriteAction {
      editor.getDocument.deleteString(closingQuotesOffset, closingQuotesOffset + QuotesLength)
    }
  }

  private def isInsideEmptyXmlAttributeValue(element: PsiElement): Boolean =
    element.elementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER && (element.getNextSibling match {
      case null => false
      case prev => prev.elementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER
    })

  private def needCorrectWiki(element: PsiElement): Boolean = {
    (element.getNode.getElementType.isInstanceOf[ScalaDocSyntaxElementType] || element.textMatches("{{{")) &&
      (element.getParent.getLastChild != element ||
        element.textMatches("'''") && element.getPrevSibling != null &&
          element.getPrevSibling.textMatches("'"))
  }

  private def handleLeftBrace(offset: Int, element: PsiElement, file: PsiFile, editor: Editor): Unit = {
    for {
      BraceWrapInfo(element, _, parent, _) <- ScalaTypedHandler.findElementToWrap(element)
      if element.isInstanceOf[ScBlockExpr]
      block = element.asInstanceOf[ScBlockExpr]
      if canRemoveClosingBrace(block)
      rBrace <- block.getRBrace
      project = file.getProject
      tabSize = CodeStyle.getSettings(project).getTabSize(ScalaFileType.INSTANCE)
      if IndentUtil.compare(rBrace, parent, tabSize) >= 0
    } {
      val (start, end) = PsiTreeUtil.nextLeaf(rBrace) match {
        case ws: PsiWhiteSpace if !ws.textContains('\n') =>
          (rBrace.startOffset, ws.endOffset)
        case _ =>
          val start = PsiTreeUtil.prevLeaf(rBrace) match {
            case ws@Whitespace(wsText) => ws.startOffset + wsText.lastIndexOf('\n').max(0)
            case _ => rBrace.startOffset
          }
          (start, rBrace.startOffset + 1)
      }

      val document = editor.getDocument
      document.deleteString(start, end)
      document.commit(project)
    }
  }

  private def canRemoveClosingBrace(block: ScBlockExpr): Boolean = {
    block.statements.size <= 1
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
