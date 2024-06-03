package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaDocTypedHandler._
import org.jetbrains.plugins.scala.editor.{DocumentExt, ScalaEditorUtils}
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, DocWhitespace, ElementType, ObjectExt, PsiElementExt, ToNullSafe}
import org.jetbrains.plugins.scala.highlighter.ScalaCommenter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.ScalaIsCommentComplete
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

final class ScalaDocTypedHandler extends TypedHandlerDelegate {

  override def charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (!file.is[ScalaFile])
      return Result.CONTINUE

    val offset = editor.getCaretModel.getOffset
    val document = editor.getDocument
    val element = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, document.getTextLength - 1, offset - 1)
    if (element == null)
      return Result.CONTINUE
    if (!isInDocComment(element))
      return Result.CONTINUE

    val documentText = document.getImmutableCharSequence

    //ensure that we are after `/**`
    if (offset < 3 || documentText.length <= offset)
      return Result.CONTINUE

    val SingleQuote = '\''

    val chatAtOffset = documentText.charAt(offset)
    chatAtOffset match {
      case ' ' | '\n' | '\t' | '\r' | SingleQuote =>
      case _ =>
        return Result.CONTINUE
    }

    val parentElementType = element.getParent.elementType
    val isInsideCodeFragment = parentElementType == ScalaDocTokenType.DOC_INNER_CODE_TAG
    if (isInsideCodeFragment) {
      return Result.CONTINUE
    }

    if (charTyped == '@') {
      adjustStartTagIndent(document, file, offset)
      return Result.STOP
    }

    def completeScalaDocWikiSyntax(tagToInsert: String): Unit = {
      insertAndCommit(offset, tagToInsert, document, project)
    }

    //Special handling of Italic & Bold syntax: ''italic text'', '''bold text'''
    //Examples:
    //type:   'CARET      result: 'CARET      (do nothing)
    //type:   ''CARET     result: ''CARET''   (close "italic" tag)
    //type:   '''CARET    result: '''CARET''' (extend "italic" tag to "bold" tag)
    if (charTyped == SingleQuote) {
      //reminder: the document text already has the typed `'` symbol
      val singleQuoteCountLeft = countChars(SingleQuote, documentText, offset - 1, -1)
      val singleQuoteCountRight = countChars(SingleQuote, documentText, offset, +1)
      val lackingClosingSingleQuotesCount =
        if (singleQuoteCountLeft == 2 || singleQuoteCountLeft == 3)
          singleQuoteCountLeft - singleQuoteCountRight
        else
          0

      if (lackingClosingSingleQuotesCount > 0) {
        val closingChars = SingleQuote.toString * lackingClosingSingleQuotesCount
        completeScalaDocWikiSyntax(closingChars)
      }
      return Result.STOP
    }

    val prevText1 = documentText.substring(offset - 1, offset)
    wikiOpenTagToCloseTag1.get(prevText1) match {
      case Some(closingTag) =>
        completeScalaDocWikiSyntax(closingTag)
        return Result.STOP
      case _ =>
    }

    val prevText2 = documentText.substring(offset - 2, offset)
    wikiOpenTagToCloseTag2.get(prevText2) match {
      case Some(closingTag) =>
        completeScalaDocWikiSyntax(closingTag)
        return Result.STOP
      case _ =>
    }

    val prevText3 = documentText.substring(offset - 3, offset)
    wikiOpenTagToCloseTag3.get(prevText3) match {
      case Some(closingTag) =>
        completeScalaDocWikiSyntax(closingTag)
        return Result.STOP
      case _ =>
    }

    Result.CONTINUE
  }

  override def beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result = {
    if (!file.is[ScalaFile])
      return Result.CONTINUE

    val offset = editor.getCaretModel.getOffset
    val prevElement = file.findElementAt(offset - 1)
    val element = file.findElementAt(offset)
    if (element == null)
      return Result.CONTINUE

    if (!ScalaDocTypedHandler.isInDocComment(element))
      return Result.CONTINUE

    val elementType = element.getNode.getElementType

    def moveCaret(): Unit = {
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
    }

    if (c == ' ' && prevElement != null && needClosingScaladocTag(editor, element, prevElement)) {
      //when typing `/**` + SPACE auto-insert closing `*/`
      //(this behaviour is similar to generating scaladoc on enter press after `/**`), see SCL-15095
      insertAndCommit(offset, """  */""", editor.getDocument, editor.getProject)
      moveCaret()
      Result.STOP
    } else if (isClosingScaladocTagOrMarkup(c, element, elementType)) {
      moveCaret()
      Result.STOP
    }
    else Result.CONTINUE
  }

  //inspired by com.intellij.codeInsight.editorActions.JavadocTypedHandler.adjustStartTagIndent
  private def adjustStartTagIndent(
    document: Document,
    file: PsiFile,
    offset: Int
  ): Unit = {
    val elementAtCaret = file.findElementAt(offset)

    val caretIsAtEmptyScaladocContentLine =
      if (elementAtCaret != null && elementAtCaret.elementType == ScalaDocTokenType.DOC_WHITESPACE) {
        val prevLeaf = PsiTreeUtil.prevLeaf(elementAtCaret)
        prevLeaf != null && prevLeaf.elementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS && {
          val nextLeaf = PsiTreeUtil.nextLeaf(elementAtCaret)
          nextLeaf match {
            case ElementType(ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS | ScalaDocTokenType.DOC_COMMENT_END) =>
              true
            case _ =>
              false
          }
        }
      }
      else false

    if (caretIsAtEmptyScaladocContentLine) {
      //we typed `@` right after `*` followed by some whitespace without any content after it
      //(* and @ are the only chars on the line)
      val whitespaceStart = elementAtCaret.getTextRange.getStartOffset
      val whitespaceEnd = offset - 1
      document.replaceString(whitespaceStart, whitespaceEnd, " ")
    }
  }

  private def isClosingScaladocTagOrMarkup(c: Char, element: PsiElement, elementType: IElementType) =
    (elementType.is[ScalaDocSyntaxElementType] || elementType == ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG) &&
      element.getParent.getLastChild == element && element.getText.startsWith(c.toString) &&
      // handling case when `'` was type right after second `'` inside `''<CARET>'' to enable bold syntax (`'''BOLD SYNTAX'''`)
      !(elementType == ScalaDocTokenType.DOC_ITALIC_TAG &&
        element.getPrevSibling.nullSafe.map(_.getNode.getElementType).get == ScalaDocTokenType.DOC_ITALIC_TAG)

  private def needClosingScaladocTag(editor: Editor, element: PsiElement, prevElement: PsiElement): Boolean =
    prevElement.elementType == ScalaDocTokenType.DOC_COMMENT_START && (prevElement.getParent match {
      case comment: ScDocComment =>
        val isAtNewLine = element match {
          case DocWhitespace(ws) => ws.contains("\n")
          case _ => false
        }
        isAtNewLine && !ScalaIsCommentComplete.isCommentComplete(comment, ScalaCommenter, editor)
      case _ =>
        false
    })

  private def insertAndCommit(offset: Int, text: String, document: Document, project: Project): Unit = {
    document.insertString(offset, text)
    document.commit(project)
  }
}

object ScalaDocTypedHandler {

  private val wikiOpenTagToCloseTag1 = Map(
    "^" -> "^",
    "`" -> "`"
  )
  private val wikiOpenTagToCloseTag2 = Map(
    "__" -> "__",
    //"''" -> "''", //`'` symbol has special handling
    ",," -> ",,",
    "[[" -> "]]"
  )
  private val wikiOpenTagToCloseTag3 = Map(
    //"'''" -> "'''", //`'` symbol has special handling
    "{{{" -> "}}}",
  )

  def isInDocComment(element: PsiElement): Boolean = {
    ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(element.getNode.getElementType)
  }

  /**
   * @return number of chars in a row equal to `charToCount` in given `text` moving index left or right depending on step (e.g. -1 or +1)
   */
  private def countChars(charToCount: Char, text: CharSequence, offset: Int, step: Int): Int = {
    var result = 0

    val textLength = text.length()
    var idx = offset
    var continue = true
    while (idx > 0 && idx < textLength && continue) {
      val charAt = text.charAt(idx)
      if (charAt == charToCount)
        result += 1
      else
        continue = false
      idx += step
    }

    result
  }
}
