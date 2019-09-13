package org.jetbrains.plugins.scala
package highlighter

import java.{util => ju}

import com.intellij.lexer.{HtmlHighlightingLexer, LayeredLexer, Lexer, StringLiteralLexer}
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.{FileTypeManager, SyntaxHighlighterBase}
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.{StringEscapesTokenTypes, TokenType}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes, ScalaXmlLexer, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.{ScalaDocLexer, ScalaDocTokenType}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes

final class ScalaSyntaxHighlighter(baseLexer: Lexer) extends SyntaxHighlighterBase {

  import ScalaSyntaxHighlighter._

  override def getHighlightingLexer: LayeredLexer =
    new CompoundLexer(baseLexer)

  override def getTokenHighlights(elementType: IElementType): Array[TextAttributesKey] =
    SyntaxHighlighterBase.pack(
      Attributes0.get(elementType).orNull,
      Attributes.get(elementType).orNull
    )
}

object ScalaSyntaxHighlighter {

  import ScalaDocTokenType._
  import ScalaTokenTypes._
  import ScalaXmlTokenTypes._

  // Comments
  private val tLINE_COMMENTS = TokenSet.create(tLINE_COMMENT)

  private val tBLOCK_COMMENTS = TokenSet.create(
    tBLOCK_COMMENT,
    tSH_COMMENT
  )

  // XML tags
  private val tXML_TAG = TokenSet.create(
    tOPENXMLTAG, tCLOSEXMLTAG, tXMLTAGPART, tBADCLOSEXMLTAG,
    XML_PI_START,
    XML_PI_END,
    XML_TAG_CHARACTERS,
    XML_WHITE_SPACE
  )

  private val tXML_TAG_NAME = TokenSet.create(
    XML_TAG_NAME
  )

  private val tXML_TAG_DATA = TokenSet.create(
    XML_DATA_CHARACTERS,
    XML_CDATA_START,
    XML_CDATA_END
  )

  private val tXML_ATTRIBUTE_NAME = TokenSet.create(
    XML_ATTRIBUTE_NAME,
    XML_EQ
  )

  private val tXML_ATTRIBUTE_VALUE = TokenSet.create(
    XML_ATTRIBUTE_VALUE_START_DELIMITER,
    XML_ATTRIBUTE_VALUE_TOKEN,
    XML_ATTRIBUTE_VALUE_END_DELIMITER
  )

  private val tXML_COMMENT = TokenSet.create(
    tXML_COMMENT_START, tXML_COMMENT_END,
    XML_COMMENT_START,
    XML_COMMENT_END,
    XML_COMMENT_CHARACTERS
  )

  //Html escape sequences
  private val tSCALADOC_HTML_ESCAPE = TokenSet.create(
    DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT
  )

  // XML tags in ScalaDoc
  private val tSCALADOC_HTML_TAGS = TokenSet.create(
    XML_TAG_NAME,
    XML_START_TAG_START,
    XML_EMPTY_ELEMENT_END,
    XML_END_TAG_START,
    XML_TAG_END
  )

  //ScalaDoc Wiki syntax elements
  private val tSCALADOC_WIKI_SYNTAX = ALL_SCALADOC_SYNTAX_ELEMENTS

  //for value in @param value
  private val tDOC_TAG_PARAM = TokenSet.create(
    DOC_TAG_VALUE_TOKEN
  )

  // Strings
  private val tSTRINGS = TokenSet.create(
    tSTRING,
    tMULTILINE_STRING,
    tWRONG_STRING,
    tCHAR,
    tSYMBOL,
    tINTERPOLATED_MULTILINE_STRING,
    tINTERPOLATED_STRING,
    tINTERPOLATED_STRING_ID,
    tINTERPOLATED_STRING_END
  )

  private val tINTERPOLATED_STRINGS = TokenSet.create(
    tINTERPOLATED_STRING_INJECTION
  )

  // Valid escape in string
  private val tVALID_STRING_ESCAPE = TokenSet.create(
    StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN,
    tINTERPOLATED_STRING_ESCAPE
  )

  // Invalid character escape in string
  private val tINVALID_CHARACTER_ESCAPE = TokenSet.create(
    StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
  )

  // Invalid unicode escape in string
  private val tINVALID_UNICODE_ESCAPE = TokenSet.create(
    StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
  )

  //ScalaDoc comment tags like @see
  private val tCOMMENT_TAGS = TokenSet.create(
    DOC_TAG_NAME
  )

  private val Attributes0: Map[IElementType, TextAttributesKey] = {
    import DefaultHighlighter._
    attributesMap(
      tXML_TAG -> XML_TAG,
      tXML_TAG_NAME -> XML_TAG,
      tXML_ATTRIBUTE_NAME -> XML_TAG,
      tXML_ATTRIBUTE_VALUE -> XML_TAG
    )
  }

  private val Attributes: Map[IElementType, TextAttributesKey] = {
    import DefaultHighlighter._
    attributesMap(
      tLINE_COMMENTS -> LINE_COMMENT,
      tBLOCK_COMMENTS -> BLOCK_COMMENT,
      TokenSet.create(ScalaDocElementTypes.SCALA_DOC_COMMENT) -> DOC_COMMENT,
      KEYWORDS -> KEYWORD,
      NUMBER_TOKEN_SET -> NUMBER,
      tVALID_STRING_ESCAPE -> VALID_STRING_ESCAPE,
      tINVALID_CHARACTER_ESCAPE -> INVALID_STRING_ESCAPE,
      tINVALID_UNICODE_ESCAPE -> INVALID_STRING_ESCAPE,
      tSTRINGS -> STRING,
      BRACES_TOKEN_SET -> BRACES,
      BRACKETS_TOKEN_SET -> BRACKETS,
      PARENTHESIS_TOKEN_SET -> PARENTHESES,
      TokenSet.create(tSEMICOLON) -> SEMICOLON,
      TokenSet.create(tDOT) -> DOT,
      TokenSet.create(tCOMMA) -> COMMA,

      TokenSet.create(tASSIGN) -> ASSIGN,
      TokenSet.create(tFUNTYPE) -> ARROW,
      tCOMMENT_TAGS -> SCALA_DOC_TAG,
      TokenSet.orSet(
        TokenSet.andNot(ALL_SCALADOC_TOKENS, tCOMMENT_TAGS),
        TokenSet.create(DOC_COMMENT_BAD_CHARACTER, DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT)
      ) -> DOC_COMMENT,
      tSCALADOC_HTML_TAGS -> SCALA_DOC_HTML_TAG,
      tSCALADOC_WIKI_SYNTAX -> SCALA_DOC_WIKI_SYNTAX,
      tSCALADOC_HTML_ESCAPE -> SCALA_DOC_HTML_ESCAPE,

      tXML_TAG_NAME -> XML_TAG_NAME,
      tXML_TAG_DATA -> XML_TAG_DATA,
      tXML_ATTRIBUTE_NAME -> XML_ATTRIBUTE_NAME,
      tXML_ATTRIBUTE_VALUE -> XML_ATTRIBUTE_VALUE,
      tXML_COMMENT -> XML_COMMENT,

      tDOC_TAG_PARAM -> SCALA_DOC_TAG_PARAM_VALUE,
      tINTERPOLATED_STRINGS -> INTERPOLATED_STRING_INJECTION
    )
  }

  private def attributesMap(attributes: (TokenSet, TextAttributesKey)*): Map[IElementType, TextAttributesKey] = {
    for {
      (tokenSet, key) <- Map(attributes: _*)
      typ <- tokenSet.getTypes
    } yield typ -> key
  }

  private class CompoundLexer(baseLexer: Lexer) extends LayeredLexer(baseLexer) {

    registerSelfStoppingLayer(
      new StringLiteralLexer('\"', tSTRING),
      Array[IElementType](tSTRING),
      IElementType.EMPTY_ARRAY
    )

    registerSelfStoppingLayer(
      new StringLiteralLexer('\'', tSTRING),
      Array[IElementType](tCHAR),
      IElementType.EMPTY_ARRAY
    )

    //interpolated string highlighting
    registerLayer(
      new LayeredLexer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tINTERPOLATED_STRING)),
      tINTERPOLATED_STRING
    )

    //scaladoc highlighting
    val scalaDocLexer = new LayeredLexer(new ScalaDocLexerHighlightingWrapper)
    scalaDocLexer.registerLayer(
      new ScalaHtmlHighlightingLexerWrapper,
      DOC_COMMENT_DATA
    )
    registerSelfStoppingLayer(
      scalaDocLexer,
      Array[IElementType](ScalaDocElementTypes.SCALA_DOC_COMMENT),
      IElementType.EMPTY_ARRAY
    )
  }

  private[highlighter] class CustomScalaLexer(isScala3: Boolean)
                                             (implicit project: Project)
    extends ScalaLexer(isScala3, project) {

    private var openingTags: ju.Stack[String] = new ju.Stack
    private var tagMatch: Boolean = false
    private var isInClosingTag: Boolean = false
    private var afterStartTagStart: Boolean = false
    private var nameIndex: Int = 0

    override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
      setScalaLexer()
      myCurrentLexer.start(buffer, startOffset, endOffset, initialState)
      myBraceStack.clear()
      myLayeredTagStack.clear()
      myXmlState = 0
      myBuffer = buffer
      myBufferEnd = endOffset
      myTokenType = null
      openingTags = new ju.Stack[String]
      tagMatch = false
      isInClosingTag = false
      afterStartTagStart = false
      nameIndex = 0
    }

    override def getTokenType: IElementType = super.getTokenType match {
      case TokenType.WHITE_SPACE => XML_WHITE_SPACE
      case XML_START_TAG_START => tOPENXMLTAG
      case XML_TAG_END if isInClosingTag =>
        if (tagMatch) tCLOSEXMLTAG
        else tBADCLOSEXMLTAG
      case XML_NAME =>
        if (nameIndex == 0) XML_TAG_NAME
        else XML_ATTRIBUTE_NAME
      case XML_EMPTY_ELEMENT_END => tCLOSEXMLTAG
      case XML_COMMENT_START => tXML_COMMENT_START
      case XML_COMMENT_END => tXML_COMMENT_END
      case elementType =>
        if (tSCALADOC_HTML_TAGS.contains(elementType)) tXMLTAGPART
        else elementType
    }

    override def advance(): Unit = {
      val tokenType = super.getTokenType
      val tokenText = getTokenText
      super.advance()

      tokenType match {
        case XML_NAME =>
          nameIndex += 1
        case XML_TAG_END |
             XML_EMPTY_ELEMENT_END =>
          nameIndex = 0
        case _ =>
      }

      tokenType match {
        case XML_END_TAG_START =>
          isInClosingTag = true
        case XML_EMPTY_ELEMENT_END =>
          if (!openingTags.empty) {
            openingTags.pop
          }
        case XML_TAG_END if isInClosingTag =>
          isInClosingTag = false
          if (tagMatch) {
            openingTags.pop
          }
        case XML_NAME if afterStartTagStart || isInClosingTag =>
          if (!isInClosingTag) {
            openingTags.push(tokenText)
          } else {
            tagMatch = !openingTags.empty && openingTags.peek == tokenText
          }
          afterStartTagStart = false
        case XML_START_TAG_START =>
          afterStartTagStart = true
        case _ =>
      }
    }
  }

  class ScalaHtmlHighlightingLexerWrapper private[highlighter]()
    extends HtmlHighlightingLexer(FileTypeManager.getInstance.getStdFileType("CSS")) {

    override def getTokenType: IElementType = {
      val htmlTokenType: IElementType = ScalaXmlLexer.ScalaXmlTokenType(super.getTokenType)
      htmlTokenType match {
        case XML_CHAR_ENTITY_REF =>
          DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT
        case XML_DATA_CHARACTERS |
             XML_BAD_CHARACTER |
             XML_WHITE_SPACE |
             XmlTokenType.XML_REAL_WHITE_SPACE =>
          // TODO: for some reason XmlTokenType.XML_REAL_WHITE_SPACE "leaks" here,
          //  should it should be XML_WHITE_SPACE
          DOC_COMMENT_DATA
        case _ if XML_COMMENTS.contains(htmlTokenType) =>
          DOC_COMMENT_DATA
        case _ =>
          htmlTokenType
      }
    }
  }

  private class ScalaDocLexerHighlightingWrapper extends ScalaDocLexer {
    private val elements = new ju.Stack[IElementType]

    override def getTokenType: IElementType = {
      val tokenType = super.getTokenType
      tokenType match {
        case `tDOT` | `tIDENTIFIER` =>
          DOC_COMMENT_DATA
        case _ if !elements.isEmpty && (elements.peek eq DOC_COMMON_CLOSE_WIKI_TAG) =>
          DOC_COMMON_CLOSE_WIKI_TAG
        case _ =>
          tokenType
      }
    }

    override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
      elements.clear()
      super.start(buffer, startOffset, endOffset, initialState)
    }

    override def advance(): Unit = {
      super.advance()

      if (!elements.isEmpty && (elements.peek eq DOC_COMMON_CLOSE_WIKI_TAG)) {
        elements.pop
        elements.pop //will never be empty there
      }

      val token: IElementType = super.getTokenType
      if (ScalaDocLexerHighlightingWrapper.SYNTAX_TO_SWAP.contains(token)) {
        if (elements.isEmpty || (elements.peek ne token)) {
          elements.push(token)
        } else {
          elements.push(DOC_COMMON_CLOSE_WIKI_TAG)
        }
      }
    }
  }

  private object ScalaDocLexerHighlightingWrapper {
    private val SYNTAX_TO_SWAP = TokenSet.andNot(
      tSCALADOC_WIKI_SYNTAX,
      TokenSet.create(
        DOC_LINK_TAG,
        DOC_LINK_CLOSE_TAG,
        DOC_HTTP_LINK_TAG,
        DOC_INNER_CODE_TAG,
        DOC_INNER_CLOSE_CODE_TAG
      )
    )
  }

}