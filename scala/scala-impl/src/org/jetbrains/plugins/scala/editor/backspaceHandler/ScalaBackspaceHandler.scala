package org.jetbrains.plugins.scala
package editor.backspaceHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor._
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler.BraceWrapInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlStartTag
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil

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
      correctMultilineString(file, editor, offset, element.getTextOffset + element.getTextLength - 3)
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
      TokenSets.INTERPOLATED_PREFIX_TOKEN_SET.contains(element.getPrevSibling.getNode.getElementType)
    ) {
      correctMultilineString(file, editor, offset, element.getParent.getLastChild.getTextOffset)
    } else if (c == '{' && ScalaApplicationSettings.getInstance.WRAP_SINGLE_EXPRESSION_BODY) {
      handleLeftBrace(offset, element, file, editor)
    }
  }

  private def correctMultilineString(file: PsiFile, editor: Editor, offset: Int, closingQuotesOffset: Int): Unit = {
    val stingIsEmpty = closingQuotesOffset == offset
    if(stingIsEmpty && ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES) {
      inWriteAction {
        editor.getDocument.deleteString(closingQuotesOffset, closingQuotesOffset + 3)
        //editor.getCaretModel.moveCaretRelatively(-1, 0, false, false, false) //https://youtrack.jetbrains.com/issue/SCL-6490
        PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
      }
    }
  }

  private def needCorrectWiki(element: PsiElement): Boolean = {
    (element.getNode.getElementType.isInstanceOf[ScaladocSyntaxElementType] || element.getText == "{{{") &&
      (element.getParent.getLastChild != element ||
        element.getText == "'''" && element.getPrevSibling != null &&
          element.getPrevSibling.getText == "'")
  }

  private def handleLeftBrace(offset: Int, element: PsiElement, file: PsiFile, editor: Editor): Unit = {
    for {
      BraceWrapInfo(element, _, parent, _) <- ScalaTypedHandler.findElementToWrap(element)
      if element.isInstanceOf[ScBlockExpr]
      block = element.asInstanceOf[ScBlockExpr]
      if canRemoveClosingBrace(block)
      rBrace <- block.getRBrace.map(_.getPsi())
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
