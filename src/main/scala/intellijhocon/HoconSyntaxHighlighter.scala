package intellijhocon

import com.intellij.openapi.fileTypes.{SyntaxHighlighterFactory, SyntaxHighlighter}
import com.intellij.psi.tree.IElementType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.lexer.{StringLiteralLexer, LayeredLexer}

class HoconSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  def getSyntaxHighlighter(project: Project, virtualFile: VirtualFile) =
    HoconSyntaxHighlighter
}

object HoconSyntaxHighlighter extends SyntaxHighlighter {

  import HoconTokenType._
  import intellijhocon.{HoconHighlighterColors => HHC}

  private val tokenHighlights = Map[IElementType, Array[TextAttributesKey]](
    BadCharacter -> Array(HHC.BadCharacter),
    QuotedString -> Array(HHC.QuotedString),
    MultilineString -> Array(HHC.QuotedString),
    HashComment -> Array(HHC.HashComment),
    DoubleSlashComment -> Array(HHC.DoubleSlashComment),
    LBrace -> Array(HHC.Braces),
    RBrace -> Array(HHC.Braces),
    LBracket -> Array(HHC.Brackets),
    RBracket -> Array(HHC.Brackets),
    RefLBrace -> Array(HHC.RefBraces),
    RefRBrace -> Array(HHC.RefBraces),
    Comma -> Array(HHC.Comma),
    Equals -> Array(HHC.PathValueSeparator),
    Colon -> Array(HHC.PathValueSeparator),
    PlusEquals -> Array(HHC.PathValueSeparator),
    Dollar -> Array(HHC.ReferenceSign),
    QMark -> Array(HHC.OptionalReferenceSign),
    StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN -> Array(HHC.ValidStringEscape),
    StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN -> Array(HHC.InvalidStringEscape),
    StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN -> Array(HHC.InvalidStringEscape)
  )

  def getTokenHighlights(tokenType: IElementType) =
    tokenHighlights.get(tokenType).getOrElse(Array.empty)

  def getHighlightingLexer = new LayeredLexer(new HoconLexer) {
    registerSelfStoppingLayer(new StringLiteralLexer('\"', QuotedString), Array(QuotedString), IElementType.EMPTY_ARRAY)
  }
}
