package intellijhocon

import com.intellij.openapi.fileTypes.{SyntaxHighlighterFactory, SyntaxHighlighter}
import com.intellij.psi.tree.IElementType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.{HighlighterColors, DefaultLanguageHighlighterColors}
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
  import DefaultLanguageHighlighterColors._

  private val tokenHighlights = Map[IElementType, Array[TextAttributesKey]](
    BadCharacter -> Array(HighlighterColors.BAD_CHARACTER),
    QuotedString -> Array(STRING),
    MultilineString -> Array(STRING),
    Comment -> Array(LINE_COMMENT),
    LBrace -> Array(BRACES),
    RBrace -> Array(BRACES),
    LBracket -> Array(BRACKETS),
    RBracket -> Array(BRACKETS),
    RefLBrace -> Array(BRACES),
    RefRBrace -> Array(BRACES),
    Comma -> Array(COMMA),
    StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN -> Array(VALID_STRING_ESCAPE),
    StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN -> Array(INVALID_STRING_ESCAPE),
    StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN -> Array(INVALID_STRING_ESCAPE)
  )

  def getTokenHighlights(tokenType: IElementType) =
    tokenHighlights.get(tokenType).getOrElse(Array.empty)

  def getHighlightingLexer = new LayeredLexer(new HoconLexer) {
    registerSelfStoppingLayer(new StringLiteralLexer('\"', QuotedString), Array(QuotedString), IElementType.EMPTY_ARRAY)
  }
}
