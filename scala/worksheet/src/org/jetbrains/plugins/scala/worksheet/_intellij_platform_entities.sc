/**
 * Here is a reminder-list of platform entities to take into account when dealing with worksheets/scratchfiles:
 *
 *  - [[com.intellij.openapi.fileTypes.FileType FileType]]
 *    - [[org.jetbrains.plugins.scala.ScalaFileType]]
 *    - [[org.jetbrains.plugins.scala.worksheet.WorksheetFileType]]
 *
 *  - [[com.intellij.lang.Language Language]]
 *    - [[org.jetbrains.plugins.scala.ScalaLanguage]]
 *    - [[org.jetbrains.plugins.scala.Scala3Language]]
 *    - [[org.jetbrains.plugins.scala.worksheet.WorksheetLanguage]]
 *
 *  - [[com.intellij.lang.PsiParser PsiParser]]
 *    (not registered in xml, single for scala 2 & scala 3)
 *    - [[org.jetbrains.plugins.scala.lang.parser.ScalaParser]]
 *
 *  - [[com.intellij.lexer.Lexer Lexer]]
 *    - [[org.jetbrains.plugins.scala.lang.lexer.ScalaLexer]]
 *
 *  - [[com.intellij.lang.ParserDefinition ParserDefinition]]
 *    - [[org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition]]
 *    - [[org.jetbrains.plugins.scala.lang.parser.Scala3ParserDefinition]]
 *    - [[org.jetbrains.plugins.scala.worksheet.WorksheetParserDefinition]]
 *    - [[org.jetbrains.plugins.scala.worksheet.WorksheetParserDefinition3]]
 *
 *  - [[com.intellij.psi.LanguageSubstitutor LanguageSubstitutor]]
 *    - [[org.jetbrains.plugins.scala.lang.parser.ScalaLanguageSubstitutor]]
 *
 *  - [[com.intellij.psi.FileViewProviderFactory FileViewProviderFactory]]
 *    - [[org.jetbrains.plugins.scala.lang.psi.ScFileViewProviderFactory]]
 *    - [[org.jetbrains.plugins.scala.worksheet.WorksheetFileViewProviderFactory]]
 *
 *  - [[com.intellij.openapi.fileTypes.SyntaxHighlighterFactory SyntaxHighlighterFactory / SyntaxHighlighter]]
 *    - [[org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory]]
 *
 *  - [[com.intellij.openapi.fileTypes.EditorHighlighterProvider EditorHighlighterProvider / ScalaEditorHighlighter]]
 *    - [[org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighterProvider]]
 */
final class _intellij_platform_entities private()