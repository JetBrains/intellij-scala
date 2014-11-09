package intellijhocon
package highlight

import com.intellij.lexer.{LayeredLexer, StringLiteralLexer}
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.{SyntaxHighlighter, SyntaxHighlighterFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.tree.IElementType
import intellijhocon.highlight.{HoconHighlighterColors => HHC}
import intellijhocon.lexer.{HoconLexer, HoconTokenType}

class HoconSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  def getSyntaxHighlighter(project: Project, virtualFile: VirtualFile) =
    HoconSyntaxHighlighter
}

object HoconSyntaxHighlighter extends SyntaxHighlighter {

  import intellijhocon.lexer.HoconTokenType._

  private val tokenHighlights = Map[IElementType, Array[TextAttributesKey]](
    BadCharacter -> Array(HHC.BadCharacter),
    QuotedString -> Array(HHC.QuotedString),
    MultilineString -> Array(HHC.MultilineString),
    HashComment -> Array(HHC.HashComment),
    DoubleSlashComment -> Array(HHC.DoubleSlashComment),
    LBrace -> Array(HHC.Braces),
    RBrace -> Array(HHC.Braces),
    LBracket -> Array(HHC.Brackets),
    RBracket -> Array(HHC.Brackets),
    SubLBrace -> Array(HHC.SubBraces),
    SubRBrace -> Array(HHC.SubBraces),
    Comma -> Array(HHC.Comma),
    Equals -> Array(HHC.PathValueSeparator),
    Colon -> Array(HHC.PathValueSeparator),
    PlusEquals -> Array(HHC.PathValueSeparator),
    Dollar -> Array(HHC.SubstitutionSign),
    QMark -> Array(HHC.OptionalSubstitutionSign),
    UnquotedChars -> Array(HHC.UnquotedString),
    Period -> Array(HHC.UnquotedString),
    StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN -> Array(HHC.ValidStringEscape),
    StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN -> Array(HHC.InvalidStringEscape),
    StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN -> Array(HHC.InvalidStringEscape)
  )

  def getTokenHighlights(tokenType: IElementType) =
    tokenHighlights.getOrElse(tokenType, Array.empty)

  def getHighlightingLexer = new LayeredLexer(new HoconLexer) {
    registerSelfStoppingLayer(new StringLiteralLexer('\"', QuotedString), Array(QuotedString), IElementType.EMPTY_ARRAY)
  }
}
