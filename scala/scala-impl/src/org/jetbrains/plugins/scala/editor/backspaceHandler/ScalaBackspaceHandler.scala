package org.jetbrains.plugins.scala
package editor.backspaceHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.editor.Scala3IndentationBasedSyntaxUtils._
import org.jetbrains.plugins.scala.editor._
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler.BraceWrapInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlStartTag
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil
import org.jetbrains.plugins.scala.util.MultilineStringUtil.{MultilineQuotesLength => QuotesLength}

class ScalaBackspaceHandler extends BackspaceHandlerDelegate {
  override def beforeCharDeleted(c: Char, file: PsiFile, editor: Editor): Unit = {
    if (!file.is[ScalaFile]) return

    val document = editor.getDocument

    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset - 1)
    if (element == null) return

    def scalaSettings = ScalaApplicationSettings.getInstance

    if (needCorrectWiki(element)) {
      inWriteAction {
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
    } else if (element.getNode.getElementType == ScalaXmlTokenTypes.XML_NAME && element.getParent != null && element.getParent.is[ScXmlStartTag]) {
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
      val highlighterIterator = editor.asInstanceOf[EditorEx].getHighlighter.createIterator(offset)
      if (isInsideEmptyMultilineString(offset, highlighterIterator)) {
        if (scalaSettings.INSERT_MULTILINE_QUOTES) {
          deleteMultilineStringClosingQuotes(editor, highlighterIterator)
        }
      } else if (isInsideEmptyXmlAttributeValue(element)) {
        inWriteAction {
          val document = editor.getDocument
          document.deleteString(offset, offset + 1)
          editor.getDocument.commit(file.getProject)
        }
      }
    } else if (c == '{') {
      handleLeftBrace(element, file, editor)
    }
  }

  // TODO: simplify when parsing of incomplete multiline strings is unified for interpolated and non-interpolated strings
  //  see also ScalaQuoteHandler.startsWithMultilineQuotes
  private def isInsideEmptyMultilineString(offset: Int, highlighterIterator: HighlighterIterator): Boolean = {
    import ScalaTokenTypes._
    highlighterIterator.getTokenType match {
      case `tMULTILINE_STRING` =>
        highlighterIterator.tokenLength == 2 * QuotesLength && offset == highlighterIterator.getStart + QuotesLength
      case `tINTERPOLATED_STRING_END` =>
        highlighterIterator.tokenLength == QuotesLength && offset == highlighterIterator.getStart && {
          highlighterIterator.retreat()
          try highlighterIterator.getTokenType == tINTERPOLATED_MULTILINE_STRING && highlighterIterator.tokenLength == QuotesLength
          finally highlighterIterator.advance() // pretending we are side-affect-free =/
        }
      case _ =>
        false
    }
  }

  private def deleteMultilineStringClosingQuotes(editor: Editor, highlighterIterator: HighlighterIterator): Unit = {
    import ScalaTokenTypes._
    val closingQuotesOffset = highlighterIterator.getStart + (highlighterIterator.getTokenType match {
      case `tMULTILINE_STRING` => QuotesLength
      case `tINTERPOLATED_STRING_END` => 0
      case _ => 0
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
    (element.getNode.getElementType.is[ScalaDocSyntaxElementType] || element.textMatches("{{{")) &&
      (element.getParent.getLastChild != element ||
        element.textMatches("'''") && element.getPrevSibling != null &&
          element.getPrevSibling.textMatches("'"))
  }

  private def handleLeftBrace(element: PsiElement, file: PsiFile, editor: Editor): Unit = {
    for {
      BraceWrapInfo(element, _, parent, _) <- ScalaTypedHandler.findElementToWrap(element)
      if element.is[ScBlockExpr]
      block = element.asInstanceOf[ScBlockExpr]
      lBrace <- block.getLBrace
      rBrace <- block.getRBrace
      if canDeleteClosingBrace(block, parent, lBrace, rBrace, file)
    } {
      val document = editor.getDocument
      deleteBrace(rBrace, document)
      val project = file.getProject
      document.commit(project)
    }
  }

  private def canDeleteClosingBrace(block: ScBlockExpr, parent: PsiElement, lBrace: PsiElement, rBrace: PsiElement, file: PsiFile): Boolean = {
    val statements = block.statements
    val deleteClosingBrace = ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE
    val tabSize = CodeStyle.getSettings(file.getProject).getTabSize(ScalaFileType.INSTANCE)

    if (deleteClosingBrace)
      if (file.useIndentationBasedSyntax)
        canDeleteClosingBraceScala3IndentationBasedSyntax(statements, parent, lBrace, rBrace, tabSize)
      else
        canDeleteClosingBraceScala2(statements, parent, rBrace, tabSize)
    else
      false
  }

  private def canDeleteClosingBraceScala2(statements: Seq[ScBlockStatement], parent: PsiElement, rBrace: PsiElement, tabSize: Int): Boolean =
    if (correctClosingBraceSelected(statements, parent, rBrace, tabSize))
      statements.isEmpty || statements.size == 1 && lastStatementDoesNotBreakSemantics(statements.head, rBrace)
    else
      false

  private def canDeleteClosingBraceScala3IndentationBasedSyntax(statements: Seq[ScBlockStatement], parent: PsiElement, lBrace: PsiElement, rBrace: PsiElement, tabSize: Int): Boolean =
    if (correctClosingBraceSelected(statements, parent, rBrace, tabSize))
      statements.isEmpty ||
        hasCorrectIndentationInside(statements, parent, tabSize) &&
        hasCorrectIndentationOutside(statements, parent, rBrace, tabSize) &&
        firstStatementDoesNotBreakIndentation(statements, lBrace, rBrace) &&
        lastElementDoesNotBreakIndentation(rBrace)
    else
      false

  /**
   * Don't remove closing brace if it belongs to a different block
   * {{{
   * class A {
   *   def foo() = {<CARET>
   *     1
   * }
   * }}}
   */
  private def correctClosingBraceSelected(statements: Seq[ScBlockStatement], parent: PsiElement, rBrace: PsiElement, tabSize: Int): Boolean =
    IndentUtil.compare(rBrace, parent, tabSize) >= 0 ||
      // closing brace after statement, e.g. print(1)}, causes the above condition to fail, even though we want to delete it
      !rBrace.startsFromNewLine() && hasCorrectIndentationInside(statements, parent, tabSize)

  /**
   * Don't remove closing brace if statements within the block are indented too far left
   * {{{
   * def foo() = {<CARET>
   *   1
   * 2
   * }
   * }}}
   */
  private def hasCorrectIndentationInside(statements: Seq[ScBlockStatement], parent: PsiElement, tabSize: Int): Boolean =
    isOneLineOneStatementBlock(statements) || statements.forall(statement => IndentUtil.compare(statement, parent, tabSize) > 0)

  /**
   * Don't remove closing brace if subsequent statements would be counted towards the block
   * {{{
   * def foo() = {<CARET>
   *   1
   * }
   *   2
   * }}}
   */
  private def hasCorrectIndentationOutside(statements: Seq[ScBlockStatement], parent: PsiElement, rBrace: PsiElement, tabSize: Int): Boolean = {
    val nextLeaf = rBrace.nextVisibleLeaf(skipComments = true)
    if (nextLeaf.isEmpty)
      true
    else if (nextLeaf.get.startsFromNewLine())
      IndentUtil.compare(nextLeaf.get, parent, tabSize) <= 0
    else
      // if the next keyword continues the current statement, e.g. else after an if-block
      // delete the closing brace because the next keyword is automatically reformatted
      continuesCompoundStatement(nextLeaf.get) &&
        (rBrace.startsFromNewLine() || lastStatementDoesNotBreakSemantics(statements.last, rBrace))
  }

  /**
   * Don't remove closing brace if only the first statement would be counted towards the block
   * {{{
   * def foo() = {<CARET>1; 2}
   * }}}
   */
  private def firstStatementDoesNotBreakIndentation(statements: Seq[ScBlockStatement], lBrace: PsiElement, rBrace: PsiElement): Boolean =
    lBrace.followedByNewLine() ||
      // first statement without newline can break semantics, because the scala parser does not recognize the indentation properly
      // https://github.com/lampepfl/dotty/issues/15039
      isOneLineOneStatementBlock(statements) && lastStatementDoesNotBreakSemantics(statements.last, rBrace) && {
        val lastLeaf = rBrace.prevVisibleLeaf(skipComments = true).get // never empty because lBrace exist
        !indentedRegionCanStart(lastLeaf)
      }

  /**
   * Don't remove closing brace if the last keyword signals that the following statement should be counted towards the block
   * {{{
   * def foo() = {<CARET>
   *   if false then
   * }
   * 1
   * }}}
   */
  private def lastElementDoesNotBreakIndentation(rBrace: PsiElement): Boolean = {
    val lastLeaf = rBrace.prevVisibleLeaf(skipComments = true).get // never empty because lBrace exists
    outdentedRegionCanStart(lastLeaf)
  }

  /**
   * Utility function for recognizing one-line blocks where braces can be removed
   * {{{
   * def foo = {print(1)}
   * }}}
   */
  private def isOneLineOneStatementBlock(statements: Seq[ScBlockStatement]) =
    statements.size == 1 && !statements.head.startsFromNewLine() && {
      val prevLeaf = statements.head.prevVisibleLeaf(skipComments = true)
      prevLeaf.get.textContains('{')
    }

  /**
   * Do not delete brace if it breaks the code semantics (and leaves the code syntax correct),
   * e.g. here we can't delete the brace cause `else` will transfer to the inner `if`
   * {{{
   * if (condition1) {<CARET>
   *   if (condition2)
   *     foo()
   * } else
   *   bar()
   * }}}
   */
  private def lastStatementDoesNotBreakSemantics(statement: ScBlockStatement, rBrace: PsiElement): Boolean =
    statement match {
      case innerIf: ScIf =>
        innerIf.elseKeyword.isDefined || !isFollowedBy(rBrace, ScalaTokenTypes.kELSE)
      case innerTry: ScTry =>
        val okFromFinally = innerTry.finallyBlock.isDefined || !isFollowedBy(rBrace, ScalaTokenTypes.kFINALLY)
        val okFromCatch = innerTry.catchBlock.isDefined || !isFollowedBy(rBrace, ScalaTokenTypes.kCATCH)
        okFromFinally && okFromCatch
      case _ => true
    }

  private def isFollowedBy(element: PsiElement, elementType: IElementType): Boolean = {
    val next = element.nextVisibleLeaf(skipComments = true)
    next.isDefined && next.get.elementType == elementType
  }

  // ! Attention !
  // Modifies the document!
  // Further modifications of the document must take
  // moved positions into account!
  // Also document needs to be committed
  private def deleteBrace(brace: PsiElement, document: Document): Unit = {
    val (start, end) = PsiTreeUtil.nextLeaf(brace) match {
      case ws: PsiWhiteSpace =>
        if (ws.textContains('\n')) {
          // if brace is the only element on its line, delete the whole line
          val start = PsiTreeUtil.prevLeaf(brace) match {
            case ws: PsiWhiteSpace => ws.startOffset + StringUtils.lastIndexOf(ws.getNode.getChars, '\n').max(0)
            case _ => brace.startOffset
          }
          (start, brace.endOffset)
        } else if (!brace.prevLeaf.exists(_.isWhitespace)) {
          // leave a space for one-line statements
          // e.g.: if (true) {1}    else 2 ==> if (true) 1 else 2
          // instead of: if (true) {1}    else 2 ==> if (true) 1else 2
          (brace.startOffset, ws.endOffset - 1)
        } else {
          (brace.startOffset, ws.endOffset)
        }
      case _ =>
        (brace.startOffset, brace.endOffset)
    }

    document.deleteString(start, end)
  }

  /*
      In some cases with nested braces (like '{()}' ) IDEA can't properly handle backspace action due to
      bag in BraceMatchingUtil (it looks for every lbrace/rbrace token regardless of they are a pair or not)
      So we have to fix it in our handler
     */
  override def charDeleted(deletedChar: Char, file: PsiFile, editor: Editor): Boolean = {
    val scalaFile = file match {
      case f: ScFile => f
      case _ =>
        return false
    }
    val document = editor.getDocument
    val offset = editor.getCaretModel.getOffset

    if (offset >= document.getTextLength) {
      return false
    }

    if (CodeInsightSettings.getInstance.AUTOINSERT_PAIR_BRACKET) {
      handleAutoInsertBraces(deletedChar, offset, scalaFile, document, editor)
    }

    if (ScalaApplicationSettings.getInstance.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY && !deletedChar.isWhitespace) {
      handleDeleteAutoBrace(offset, document, scalaFile, editor)
    }

    false
  }

  private def handleAutoInsertBraces(deletedChar: Char, offset: Int, file: ScFile, document: Document, editor: Editor): Unit = {
    def hasLeft: Option[Boolean] = {
      val txt = document.getImmutableCharSequence

      val iterator = editor.asInstanceOf[EditorEx].getHighlighter.createIterator(offset)
      val tpe = iterator.getTokenType
      if (tpe == null)
        return None

      val fileType = file.getFileType
      val matcher = BraceMatchingUtil.getBraceMatcher(fileType, tpe)
      if (matcher == null)
        return None

      val stack = scala.collection.mutable.Stack[IElementType]()

      iterator.retreat()
      while (!iterator.atEnd() && iterator.getStart > 0 && iterator.getTokenType != null) {
        if (matcher.isRBraceToken(iterator, txt, fileType)) stack push iterator.getTokenType
        else if (matcher.isLBraceToken(iterator, txt, fileType)) {
          if (stack.isEmpty || !matcher.isPairBraces(iterator.getTokenType, stack.pop()))
            return Some(false)
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
    (deletedChar, charNext) match {
      case ('{', '}') => fixBrace()
      case ('(', ')') => fixBrace()
      case _ =>
    }
  }

  private def handleDeleteAutoBrace(offset: Int, document: Document, file: ScFile, editor: Editor): Unit = {
    val lineText = document.lineTextAt(offset)
    if (!containsOnlyWhitespaces(lineText)) {
      return
    }

    val project = file.getProject
    document.commit(file.getProject)

    val element = file.findElementAt(offset)

    element.parents.find(_.is[ScBlockExpr]) match {
      case Some(block: ScBlockExpr) if canAutoDeleteBraces(block) && AutoBraceUtils.isIndentationContext(block) =>
        (block.getLBrace, block.getRBrace, block.getParent) match {
          case (Some(lBrace), Some(rBrace), parent: PsiElement) =>

            val tabSize = CodeStyle.getSettings(project).getTabSize(ScalaFileType.INSTANCE)
            if (IndentUtil.compare(rBrace, parent, tabSize) >= 0) {
              val firstWhiteSpace = lBrace.nextSibling.filter(ws => ws.isWhitespace && ws.getNextSibling != rBrace)
              val caretIsOnBlockIndent = firstWhiteSpace.forall(ws =>
                IndentUtil.calcIndent(ws.getNode.getChars, tabSize) == IndentUtil.calcIndent(lineText, tabSize)
              )

              if (caretIsOnBlockIndent) {
                val document = editor.getDocument
                // delete closing brace first because deleting it doesn't change position of the left brace
                deleteBrace(rBrace, document)
                deleteBrace(lBrace, document)
                document.commit(project)
              }
            }
          case _ =>
        }
      case _ =>
    }
  }

  private def containsOnlyWhitespaces(seq: CharSequence): Boolean = {
    seq.forall(_.isWhitespace)
  }

  private def canAutoDeleteBraces(block: ScBlockExpr): Boolean = {
    val it = block.children
    var foundExpressions = false

    while (it.hasNext) {
      it.next() match {
        case _: ScExpression =>
          // return early if we already found 2 expressions/comments
          if (foundExpressions)
            return false

          foundExpressions = true
        case _: ScBlockStatement /* cannot be an expression because we matched those in the previous case */ =>
          return false
        case _: PsiComment =>
          return false
        case _ =>
      }
    }

    true
  }
}
