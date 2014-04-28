package intellijhocon

import com.intellij.openapi.fileTypes.{SyntaxHighlighterFactory, SyntaxHighlighter}
import com.intellij.psi.tree.IElementType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.{HighlighterColors, DefaultLanguageHighlighterColors}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

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
    RefStart -> Array(BRACES),
    RefEnd -> Array(BRACES),
    Comma -> Array(COMMA)
  )

  def getTokenHighlights(tokenType: IElementType) =
    tokenHighlights.get(tokenType).getOrElse(Array.empty)

  def getHighlightingLexer =
    new HoconLexer
}
