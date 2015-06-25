package org.jetbrains.plugins.scala.lang.completion.lookups

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.plugins.scala.ScalaFileType

/**
 * @author Alefas
 * @since 27.03.12
 */
object ScalaKeywordLookupItem {
  def getLookupElement(keyword: String, position: PsiElement): LookupElement = {
    val keywordPsi: PsiElement = ScalaLightKeyword(position.getManager, keyword)
    LookupElementBuilder.create(keywordPsi, keyword).withBoldness(true).withIcon(new EmptyIcon(16, 16)).
      withInsertHandler(new KeywordInsertHandler(keyword))
  }

  class KeywordInsertHandler(keyword: String) extends InsertHandler[LookupElement] {
    override def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
      import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword._
      val parentheses = Set(IF, FOR, WHILE)
      val braces = Set(CATCH, ELSE, EXTENDS, FINALLY, FOR, FOR_SOME, NEW, TRY, DO, YIELD)
      val editor = context.getEditor
      val document = editor.getDocument
      val offset = context.getStartOffset + keyword.length
      keyword match {
        case THIS | FALSE | TRUE | NULL | SUPER => // do nothing
        case _ =>
          def addSpace(addCompletionChar: Boolean = false) {
            if (context.getFile.getViewProvider.getFileType != ScalaFileType.SCALA_FILE_TYPE) { // for play2 - we shouldn't add space in templates (like @if, @while etc)
              val offset = context.getStartOffset
              val docStart = Math.max(0, context.getStartOffset - 1)

              val seq = context.getDocument.getCharsSequence.subSequence(docStart, offset)

              if (seq.length() == 1 && seq.charAt(0) == '@') return
            }

            context.setAddCompletionChar(addCompletionChar)
            if (document.getTextLength <= offset || document.getText.charAt(offset) != ' ')
              document.insertString(offset, " ")
            editor.getCaretModel.moveToOffset(offset + 1)
          }
          val settings = CodeStyleSettingsManager.getInstance(context.getProject).getCurrentSettings.getCommonSettings(ScalaFileType.SCALA_LANGUAGE)
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
