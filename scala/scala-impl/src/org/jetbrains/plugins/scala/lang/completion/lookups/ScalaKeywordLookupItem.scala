package org.jetbrains.plugins.scala
package lang
package completion
package lookups

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.{CompletionResultSet, InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.{CodeStyleManager, CommonCodeStyleSettings}
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.plugins.scala.extensions.{BooleanExt, PsiFileExt}

object ScalaKeywordLookupItem {

  def apply(keyword: String): LookupElement =
    LookupElementBuilder.create(keyword)
      .withBoldness(true)
      .withIcon(EmptyIcon.create(16, 16))
      .withInsertHandler(new KeywordInsertHandler(keyword))

  def addFor(resultSet: CompletionResultSet,
             keywords: String*): Unit = for {
    keyword <- keywords
    element = ScalaKeywordLookupItem(keyword)
  } resultSet.addElement(element)

  final class KeywordInsertHandler(val keyword: String) extends InsertHandler[LookupElement] {

    import KeywordInsertHandler._
    import ScalaKeyword._

    override def handleInsert(context: InsertionContext,
                              lookupElement: LookupElement): Unit = keyword match {
      case THIS | FALSE | TRUE | NULL | SUPER => // do nothing
      case _ =>
        implicit val InsertionContextExt(editor, document, file, project) = context

        val (isLeftParen, isLeftCurly, isLeftSquare) = bracketKind(context.getCompletionChar)
        val maybeAddCompletionChar = keyword match {
          case IF if isLeftParen => bySetting(_.SPACE_BEFORE_IF_PARENTHESES)
          case FOR if isLeftParen => bySetting(_.SPACE_BEFORE_FOR_PARENTHESES)
          case WHILE if isLeftParen => bySetting(_.SPACE_BEFORE_WHILE_PARENTHESES)
          case EXTENDS |
               FOR_SOME |
               NEW if isLeftCurly => Some(true)
          case CATCH if isLeftCurly => bySetting(_.SPACE_BEFORE_CATCH_LBRACE)
          case ELSE if isLeftCurly => bySetting(_.SPACE_BEFORE_ELSE_LBRACE)
          case FINALLY if isLeftCurly => bySetting(_.SPACE_BEFORE_FINALLY_LBRACE)
          case FOR if isLeftCurly => bySetting(_.SPACE_BEFORE_FOR_LBRACE)
          case TRY if isLeftCurly => bySetting(_.SPACE_BEFORE_TRY_LBRACE)
          case DO if isLeftCurly => bySetting(_.SPACE_BEFORE_DO_LBRACE)
          case YIELD if isLeftCurly => bySetting(_.SPACE_BEFORE_FOR_LBRACE)
          case PRIVATE |
               PROTECTED if isLeftSquare => None
          case _ => Some(false)
        }

        val targetRange = TextRange.from(context.getStartOffset, keyword.length)
        val caretModel = editor.getCaretModel

        for {
          addCompletionChar <- maybeAddCompletionChar
          if isValidScalaPrefixAt(targetRange.getStartOffset)
        } {
          context.setAddCompletionChar(addCompletionChar)

          val endOffset = targetRange.getEndOffset
          if (!(endOffset < document.getTextLength &&
            document.getImmutableCharSequence.charAt(endOffset) == ' ')) {
            document.insertString(endOffset, " ")
          }
          caretModel.moveToOffset(endOffset + 1)
        }

        keyword match {
          case CASE =>
            adjustLineIndent(targetRange)
          case MATCH =>
            val caretOffset = caretModel.getOffset

            val useIndentationBasedSyntax = file.useIndentationBasedSyntax
            val text = if (useIndentationBasedSyntax) s"\n$CASE" else s"{\n$CASE\n}"
            document.insertString(caretOffset, text)
            adjustLineIndent(TextRange.from(caretOffset, text.length))

            val endOffset = document.getLineEndOffset(document.getLineNumber(caretOffset) + (!useIndentationBasedSyntax).toInt)
            document.insertString(endOffset, " ")
            caretModel.moveToOffset(endOffset + 1)
          case _ =>
        }
    }
  }

  private object KeywordInsertHandler {

    private def bracketKind(completionChar: Char) = completionChar match {
      case '(' => (true, false, false)
      case '{' => (false, true, false)
      case '[' => (false, false, true)
      case _ => (false, false, false)
    }

    private def isValidScalaPrefixAt(startOffset: Int)
                                    (implicit file: PsiFile,
                                     document: Document) =
      file.getViewProvider.getFileType match {
        case ScalaFileType.INSTANCE => true
        case _ =>
          val subSequence = document.getCharsSequence.subSequence(
            0 max (startOffset - 1),
            startOffset
          )

          // for play2 - we shouldn't add space in templates (like @if, @while etc)
          subSequence.length != 1 ||
            subSequence.charAt(0) != '@'
      }

    private def bySetting(value: CommonCodeStyleSettings => Boolean)
                         (implicit project: Project) = {
      val settings = CodeStyle.getSettings(project)
        .getCommonSettings(ScalaLanguage.INSTANCE)
      if (value(settings)) Some(true)
      else None
    }

    private def adjustLineIndent(rangeToAdjust: TextRange)
                                (implicit project: Project,
                                 document: Document): Unit = {
      val manager = PsiDocumentManager.getInstance(project)
      manager.commitDocument(document)

      manager.getPsiFile(document) match {
        case null =>
        case file => CodeStyleManager.getInstance(project).adjustLineIndent(file, rangeToAdjust)
      }
    }
  }
}
