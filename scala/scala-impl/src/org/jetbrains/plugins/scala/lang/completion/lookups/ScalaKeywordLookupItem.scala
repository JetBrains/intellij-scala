package org.jetbrains.plugins.scala
package lang
package completion
package lookups

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.{CompletionResultSet, InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.ui.EmptyIcon

/**
 * @author Alefas
 * @since 27.03.12
 */
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

  import ScalaKeyword._

  final class KeywordInsertHandler(keyword: String) extends InsertHandler[LookupElement] {

    override def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
      val parentheses = Set(IF, FOR, WHILE)
      val braces = Set(CATCH, ELSE, EXTENDS, FINALLY, FOR, FOR_SOME, NEW, TRY, DO, YIELD)
      val editor = context.getEditor
      val document = editor.getDocument
      val offset = context.getStartOffset + keyword.length
      keyword match {
        case THIS | FALSE | TRUE | NULL | SUPER => // do nothing
        case _ =>
          def addSpace(addCompletionChar: Boolean = false) {
            if (context.getFile.getViewProvider.getFileType != ScalaFileType.INSTANCE) {
              // for play2 - we shouldn't add space in templates (like @if, @while etc)
              val offset = context.getStartOffset
              val docStart = Math.max(0, context.getStartOffset - 1)

              val seq = context.getDocument.getCharsSequence.subSequence(docStart, offset)

              if (seq.length() == 1 && seq.charAt(0) == '@') return
            }

            context.setAddCompletionChar(addCompletionChar)
            if (document.getTextLength <= offset || document.getImmutableCharSequence.charAt(offset) != ' ')
              document.insertString(offset, " ")
            editor.getCaretModel.moveToOffset(offset + 1)
          }

          val settings = CodeStyle.getSettings(context.getProject).getCommonSettings(ScalaLanguage.INSTANCE)
          context.getCompletionChar match {
            case '(' if parentheses.contains(keyword) =>
              val add = keyword match {
                case IF => settings.SPACE_BEFORE_IF_PARENTHESES
                case FOR => settings.SPACE_BEFORE_FOR_PARENTHESES
                case WHILE => settings.SPACE_BEFORE_WHILE_PARENTHESES
              }
              if (add) addSpace(addCompletionChar = true)
            case '{' if braces.contains(keyword) =>
              val add = keyword match {
                case CATCH => settings.SPACE_BEFORE_CATCH_LBRACE
                case ELSE => settings.SPACE_BEFORE_ELSE_LBRACE
                case EXTENDS => true
                case FINALLY => settings.SPACE_BEFORE_FINALLY_LBRACE
                case FOR => settings.SPACE_BEFORE_FOR_LBRACE
                case FOR_SOME => true
                case NEW => true
                case TRY => settings.SPACE_BEFORE_TRY_LBRACE
                case DO => settings.SPACE_BEFORE_DO_LBRACE
                case YIELD => settings.SPACE_BEFORE_FOR_LBRACE
              }
              if (add) addSpace(addCompletionChar = true)
            case '[' =>
              keyword match {
                case PRIVATE | PROTECTED => //do nothing
                case _ => addSpace(addCompletionChar = false)
              }
            case _ => addSpace()
          }
          if (keyword == CASE) {
            val manager = PsiDocumentManager.getInstance(context.getProject)
            manager.commitDocument(document)
            val file = manager.getPsiFile(document)
            if (file == null) return
            CodeStyleManager.getInstance(context.getProject).
              adjustLineIndent(file, new TextRange(context.getStartOffset, offset))
          }
      }
    }
  }
}
