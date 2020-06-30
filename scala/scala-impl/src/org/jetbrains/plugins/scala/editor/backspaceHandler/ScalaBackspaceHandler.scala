package org.jetbrains.plugins.scala
package editor.backspaceHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.editor._
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler.BraceWrapInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScBlockStatement, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlStartTag
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScBlockStatement, ScIf, ScTry}
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
    } else if (!c.isWhitespace) {
      handleAutoBrace(offset, element, file, editor)
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

  private def handleAutoBrace(offset: Int, element: PsiElement, file: PsiFile, editor: Editor): Unit = {
    if (element.getTextLength != 1 ||
      !element.followedByNewLine(ignoreComments = false) ||
      !element.startsFromNewLine(ignoreComments = false)) {
      return
    }

    element.parents.find(_.is[ScBlockExpr]) match {
      case Some(block: ScBlockExpr) if canAutoRemoveBraces(block) && AutoBraceUtils.isIndentationContext(block) =>
        (block.getLBrace, block.getRBrace, block.getParent) match {
          case (Some(lBrace), Some(rBrace), parent: PsiElement) =>
            val project = element.getProject

            val tabSize = CodeStyle.getSettings(project).getTabSize(ScalaFileType.INSTANCE)
            if (IndentUtil.compare(rBrace, parent, tabSize) >= 0) {
              val document = editor.getDocument
              // remove closing brace first because removing it doesn't change position of the left brace
              deleteBrace(rBrace, document)
              deleteBrace(lBrace, document)
              document.commit(project)
            }
          case _ =>
        }
      case _ =>
    }
  }

  private def canAutoRemoveBraces(block: ScBlockExpr): Boolean = {
    val it = block.children
    var foundCommentsOrExpressions = 0

    while (it.hasNext) {
      it.next() match {
        case _: PsiComment | _: ScExpression =>
          foundCommentsOrExpressions += 1

          // return early if we already found 2 expressions/comments
          if (foundCommentsOrExpressions > 2)
            return false
        case _: ScBlockStatement /* cannot be an expression because we matched those in the previous case */  =>
          return false
        case _ =>
      }
    }
    foundCommentsOrExpressions == 2
  }

  private def handleLeftBrace(offset: Int, element: PsiElement, file: PsiFile, editor: Editor): Unit = {
    for {
      BraceWrapInfo(element, _, parent, _) <- ScalaTypedHandler.findElementToWrap(element)
      if element.isInstanceOf[ScBlockExpr]
      block = element.asInstanceOf[ScBlockExpr]
      rBrace <- block.getRBrace
      if canRemoveClosingBrace(block, rBrace)
      project = file.getProject
      tabSize = CodeStyle.getSettings(project).getTabSize(ScalaFileType.INSTANCE)
      if IndentUtil.compare(rBrace, parent, tabSize) >= 0
    } {
      val document = editor.getDocument
      deleteBrace(rBrace, document)
      document.commit(project)
    }
  }

  // ! Attention !
  // Modifies the document!
  // Further modifications of the document must take
  // moved positions into account!
  // Also document needs to be commited
  private def deleteBrace(brace: PsiElement, document: Document): Unit = {
    val (start, end) = PsiTreeUtil.nextLeaf(brace) match {
      case ws: PsiWhiteSpace =>
        if (ws.textContains('\n')) {
          val start = PsiTreeUtil.prevLeaf(brace) match {
            case ws: PsiWhiteSpace => ws.startOffset + StringUtils.lastIndexOf(ws.getNode.getChars, '\n').max(0)
            case _                 => brace.startOffset
          }
          (start, brace.endOffset)
        } else {
          (brace.startOffset, ws.endOffset)
        }
      case _ =>
        (brace.startOffset, brace.endOffset)
    }

    document.deleteString(start, end)
  }

  private def canRemoveClosingBrace(block: ScBlockExpr, blockRBrace: PsiElement): Boolean = {
    val statements = block.statements

    if (statements.isEmpty)
      true
    else if (statements.size == 1)
      canRemoveClosingBrace(statements.head, blockRBrace)
    else
      false
  }

  /**
   * do not remove brace if it breaks the code semantics (and leaves the code syntax correct)
   * e.g. here we can't remove the brace cause `else` will transfer to the inner `if`
   * {{{
   * if (condition1) {<CARET>
   *   if (condition2)
   *     foo()
   * } else
   *   bar()
   * }}}
   */
  private def canRemoveClosingBrace(statement: ScBlockStatement, blockRBrace: PsiElement) =
    statement match {
      case innerIf: ScIf   =>
        innerIf.elseKeyword.isDefined || !isFollowedBy(blockRBrace, ScalaTokenTypes.kELSE)
      case innerTry: ScTry =>
        val okFromFinally = innerTry.finallyBlock.isDefined || !isFollowedBy(blockRBrace, ScalaTokenTypes.kFINALLY)
        val okFromCatch   = innerTry.catchBlock.isDefined || !isFollowedBy(blockRBrace, ScalaTokenTypes.kCATCH)
        okFromFinally && okFromCatch
      case _               => true
    }

  private def isFollowedBy(element: PsiElement, elementType: IElementType): Boolean = {
    val next = element.getNextNonWhitespaceAndNonEmptyLeaf
    next != null && next.elementType == elementType
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
