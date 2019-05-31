package org.jetbrains.plugins.scala.highlighter

import java.{util => ju}

import com.intellij.lexer.{HtmlHighlightingLexer, LayeredLexer, Lexer, StringLiteralLexer}
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.{FileTypeManager, SyntaxHighlighterBase}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.{StringEscapesTokenTypes, TokenType}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes, ScalaXmlLexer, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.{ScalaDocLexer, ScalaDocTokenType}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes

class ScalaSyntaxHighlighter private[highlighter](val treatDocCommentAsBlockComment: Boolean) extends SyntaxHighlighterBase {
  def this() = this(false)

  override def getHighlightingLexer: Lexer = {
    new ScalaSyntaxHighlighter.CompoundLexer(treatDocCommentAsBlockComment)
  }

  override def getTokenHighlights(iElementType: IElementType): Array[TextAttributesKey] = {
    SyntaxHighlighterBase.pack(
      ScalaSyntaxHighlighter.ATTRIBUTES0.get(iElementType),
      ScalaSyntaxHighlighter.ATTRIBUTES.get(iElementType)
    )
  }

  /**
   * This is workaround method to access protected static method of base class in companion object
   *
   * @see [[https://github.com/scala/bug/issues/1806]]
   * @see [[https://www.scala-lang.org/old/faq/4.html#4n1381]]
   */
  private def fillMap(map: ju.Map[IElementType, TextAttributesKey], keys: TokenSet, value: TextAttributesKey): Unit =
    SyntaxHighlighterBase.fillMap(map, keys, value)
}

object ScalaSyntaxHighlighter {
  // Comments
  private val tLINE_COMMENTS = TokenSet.create(
    ScalaTokenTypes.tLINE_COMMENT
  )

  private val tBLOCK_COMMENTS = TokenSet.create(
    ScalaTokenTypes.tBLOCK_COMMENT,
    ScalaTokenTypes.tSH_COMMENT
  )

  private val tDOC_COMMENTS = TokenSet.create(
    ScalaDocElementTypes.SCALA_DOC_COMMENT
  )

  // ScalaXmlTokenTypes.XML tags
  private val tXML_TAG = TokenSet.create(
    tOPENXMLTAG, tCLOSEXMLTAG, tXMLTAGPART, tBADCLOSEXMLTAG,
    ScalaXmlTokenTypes.XML_PI_START,
    ScalaXmlTokenTypes.XML_PI_END,
    ScalaXmlTokenTypes.XML_TAG_CHARACTERS,
    ScalaXmlTokenTypes.XML_WHITE_SPACE
  )

  private val tXML_TAG_NAME = TokenSet.create(
    ScalaXmlTokenTypes.XML_TAG_NAME
  )

  private val tXML_TAG_DATA = TokenSet.create(
    ScalaXmlTokenTypes.XML_DATA_CHARACTERS,
    ScalaXmlTokenTypes.XML_CDATA_START,
    ScalaXmlTokenTypes.XML_CDATA_END
  )

  private val tXML_ATTRIBUTE_NAME = TokenSet.create(
    ScalaXmlTokenTypes.XML_ATTRIBUTE_NAME,
    ScalaXmlTokenTypes.XML_EQ
  )

  private val tXML_ATTRIBUTE_VALUE = TokenSet.create(
    ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER,
    ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_TOKEN,
    ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER
  )

  private val tXML_COMMENT = TokenSet.create(
    tXML_COMMENT_START, tXML_COMMENT_END,
    ScalaXmlTokenTypes.XML_COMMENT_START,
    ScalaXmlTokenTypes.XML_COMMENT_END,
    ScalaXmlTokenTypes.XML_COMMENT_CHARACTERS
  )

  //Html escape sequences
  private val tSCALADOC_HTML_ESCAPE = TokenSet.create(
    ScalaDocTokenType.DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT
  )

  // ScalaXmlTokenTypes.XML tags in ScalaDoc
  private val tSCALADOC_HTML_TAGS = TokenSet.create(
    ScalaXmlTokenTypes.XML_TAG_NAME,
    ScalaXmlTokenTypes.XML_START_TAG_START,
    ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END,
    ScalaXmlTokenTypes.XML_END_TAG_START,
    ScalaXmlTokenTypes.XML_TAG_END
  )

  //ScalaDoc Wiki syntax elements
  private val tSCALADOC_WIKI_SYNTAX = ScalaDocTokenType.ALL_SCALADOC_SYNTAX_ELEMENTS

  //for value in @param value
  private val tDOC_TAG_PARAM = TokenSet.create(
    ScalaDocTokenType.DOC_TAG_VALUE_TOKEN
  )

  // Numbers
  private val tNUMBERS = TokenSet.create(
    ScalaTokenTypes.tINTEGER,
    ScalaTokenTypes.tFLOAT
  )

  // Braces
  private val tBRACES = TokenSet.create(
    ScalaTokenTypes.tLBRACE,
    ScalaTokenTypes.tRBRACE
  )

  // Brackets
  private val tBRACKETS = TokenSet.create(
    ScalaTokenTypes.tLSQBRACKET,
    ScalaTokenTypes.tRSQBRACKET
  )

  // Parentheses
  private val tPARENTHESES = TokenSet.create(
    ScalaTokenTypes.tLPARENTHESIS,
    ScalaTokenTypes.tRPARENTHESIS
  )

  // Strings
  private val tSTRINGS = TokenSet.create(
    ScalaTokenTypes.tSTRING,
    ScalaTokenTypes.tMULTILINE_STRING,
    ScalaTokenTypes.tWRONG_STRING,
    ScalaTokenTypes.tCHAR,
    ScalaTokenTypes.tSYMBOL,
    ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING,
    ScalaTokenTypes.tINTERPOLATED_STRING,
    ScalaTokenTypes.tINTERPOLATED_STRING_ID,
    ScalaTokenTypes.tINTERPOLATED_STRING_END
  )

  private val tINTERPOLATED_STRINGS = TokenSet.create(
    ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION
  )

  // Valid escape in string
  private val tVALID_STRING_ESCAPE = TokenSet.create(
    StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN,
    ScalaTokenTypes.tINTERPOLATED_STRING_ESCAPE
  )

  // Invalid character escape in string
  private val tINVALID_CHARACTER_ESCAPE = TokenSet.create(
    StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
  )

  // Invalid unicode escape in string
  private val tINVALID_UNICODE_ESCAPE = TokenSet.create(
    StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
  )

  // Keywords
  private val kRESERVED_WORDS = TokenSet.create(
    ScalaTokenTypes.kABSTRACT,
    ScalaTokenTypes.kCASE,
    ScalaTokenTypes.kCATCH,
    ScalaTokenTypes.kCLASS,
    ScalaTokenTypes.kDEF,
    ScalaTokenTypes.kDO,
    ScalaTokenTypes.kELSE,
    ScalaTokenTypes.kEXTENDS,
    ScalaTokenTypes.kFALSE,
    ScalaTokenTypes.kFINAL,
    ScalaTokenTypes.kFINALLY,
    ScalaTokenTypes.kFOR,
    ScalaTokenTypes.kFOR_SOME,
    ScalaTokenTypes.kIF,
    ScalaTokenTypes.kIMPLICIT,
    ScalaTokenTypes.kIMPORT,
    ScalaTokenTypes.kLAZY,
    ScalaTokenTypes.kMATCH,
    ScalaTokenTypes.kNEW,
    ScalaTokenTypes.kNULL,
    ScalaTokenTypes.kOBJECT,
    ScalaTokenTypes.kOVERRIDE,
    ScalaTokenTypes.kPACKAGE,
    ScalaTokenTypes.kPRIVATE,
    ScalaTokenTypes.kPROTECTED,
    ScalaTokenTypes.kRETURN,
    ScalaTokenTypes.kSEALED,
    ScalaTokenTypes.kSUPER,
    ScalaTokenTypes.kTHIS,
    ScalaTokenTypes.kTHROW,
    ScalaTokenTypes.kTRAIT,
    ScalaTokenTypes.kTRY,
    ScalaTokenTypes.kTRUE,
    ScalaTokenTypes.kTYPE,
    ScalaTokenTypes.kVAL,
    ScalaTokenTypes.kVAR,
    ScalaTokenTypes.kWHILE,
    ScalaTokenTypes.kWITH,
    ScalaTokenTypes.kYIELD,
    ScalaTokenTypes.kMACRO
  )

  private val tOPS = TokenSet.create(
    ScalaTokenTypes.tASSIGN
  )

  private val tARROW = TokenSet.create(
    ScalaTokenTypes.tFUNTYPE
  )

  private val tSEMICOLON = TokenSet.create(
    ScalaTokenTypes.tSEMICOLON
  )

  private val tDOT = TokenSet.create(
    ScalaTokenTypes.tDOT
  )

  private val tCOMMA = TokenSet.create(
    ScalaTokenTypes.tCOMMA
  )

  //ScalaDoc comment tags like @see
  private val tCOMMENT_TAGS = TokenSet.create(
    ScalaDocTokenType.DOC_TAG_NAME
  )

  private val ATTRIBUTES0: ju.Map[IElementType, TextAttributesKey] = new ju.HashMap
  private val ATTRIBUTES: ju.Map[IElementType, TextAttributesKey] = new ju.HashMap

  private class CompoundLexer private[highlighter](val treatDocCommentAsBlockComment: Boolean)
    extends LayeredLexer(new ScalaSyntaxHighlighter.CustomScalaLexer(treatDocCommentAsBlockComment)) {

    registerSelfStoppingLayer(
      new StringLiteralLexer('\"', ScalaTokenTypes.tSTRING),
      Array[IElementType](ScalaTokenTypes.tSTRING),
      IElementType.EMPTY_ARRAY
    )

    registerSelfStoppingLayer(
      new StringLiteralLexer('\'', ScalaTokenTypes.tSTRING),
      Array[IElementType](ScalaTokenTypes.tCHAR),
      IElementType.EMPTY_ARRAY
    )

    //interpolated string highlighting
    registerLayer(
      new LayeredLexer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, ScalaTokenTypes.tINTERPOLATED_STRING)),
      ScalaTokenTypes.tINTERPOLATED_STRING
    )

    //scaladoc highlighting
    val scalaDocLexer = new LayeredLexer(new ScalaSyntaxHighlighter.ScalaDocLexerHighlightingWrapper)
    scalaDocLexer.registerLayer(
      new ScalaSyntaxHighlighter.ScalaHtmlHighlightingLexerWrapper,
      ScalaDocTokenType.DOC_COMMENT_DATA
    )
    registerSelfStoppingLayer(
      scalaDocLexer,
      Array[IElementType](ScalaDocElementTypes.SCALA_DOC_COMMENT),
      IElementType.EMPTY_ARRAY
    )
  }

  private class CustomScalaLexer private[highlighter](val treatDocCommentAsBlockComment: Boolean) extends ScalaLexer(treatDocCommentAsBlockComment) {
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

    override def getTokenType: IElementType = {
      super.getTokenType match {
        case TokenType.WHITE_SPACE => ScalaXmlTokenTypes.XML_WHITE_SPACE
        case ScalaXmlTokenTypes.XML_START_TAG_START => tOPENXMLTAG
        case ScalaXmlTokenTypes.XML_TAG_END if isInClosingTag =>
          if (tagMatch) tCLOSEXMLTAG
          else tBADCLOSEXMLTAG
        case ScalaXmlTokenTypes.XML_NAME =>
          if (nameIndex == 0) ScalaXmlTokenTypes.XML_TAG_NAME
          else ScalaXmlTokenTypes.XML_ATTRIBUTE_NAME
        case ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END => tCLOSEXMLTAG
        case ScalaXmlTokenTypes.XML_COMMENT_START => tXML_COMMENT_START
        case ScalaXmlTokenTypes.XML_COMMENT_END => tXML_COMMENT_END
        case typ =>
          if (tSCALADOC_HTML_TAGS.contains(typ)) tXMLTAGPART
          else typ
      }
    }

    override def advance(): Unit = {
      val tokenType = super.getTokenType
      val tokenText = getTokenText
      super.advance()

      tokenType match {
        case ScalaXmlTokenTypes.XML_NAME =>
          nameIndex += 1
        case ScalaXmlTokenTypes.XML_TAG_END |
             ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END =>
          nameIndex = 0
        case _ =>
      }

      tokenType match {
        case ScalaXmlTokenTypes.XML_END_TAG_START =>
          isInClosingTag = true
        case ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END =>
          if (!openingTags.empty) {
            openingTags.pop
          }
        case ScalaXmlTokenTypes.XML_TAG_END if isInClosingTag =>
          isInClosingTag = false
          if (tagMatch) {
            openingTags.pop
          }
        case ScalaXmlTokenTypes.XML_NAME if afterStartTagStart || isInClosingTag =>
          if (!isInClosingTag) {
            openingTags.push(tokenText)
          } else {
            tagMatch = !openingTags.empty && openingTags.peek == tokenText
          }
          afterStartTagStart = false
        case ScalaXmlTokenTypes.XML_START_TAG_START =>
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
        case ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF =>
          ScalaDocTokenType.DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT
        case ScalaXmlTokenTypes.XML_DATA_CHARACTERS | ScalaXmlTokenTypes.XML_BAD_CHARACTER =>
          ScalaDocTokenType.DOC_COMMENT_DATA
        case _ if ScalaXmlTokenTypes.XML_COMMENTS.contains(htmlTokenType) =>
          ScalaDocTokenType.DOC_COMMENT_DATA
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
        case ScalaTokenTypes.tDOT | `tIDENTIFIER` =>
          ScalaDocTokenType.DOC_COMMENT_DATA
        case _ if !elements.isEmpty && (elements.peek eq ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG) =>
          ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG
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

      if (!elements.isEmpty && (elements.peek eq ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG)) {
        elements.pop
        elements.pop //will never be empty there
      }

      val token: IElementType = super.getTokenType
      if (ScalaDocLexerHighlightingWrapper.SYNTAX_TO_SWAP.contains(token)) {
        if (elements.isEmpty || (elements.peek ne token)) {
          elements.push(token)
        } else {
          elements.push(ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG)
        }
      }
    }
  }

  private object ScalaDocLexerHighlightingWrapper {
    private val SYNTAX_TO_SWAP = TokenSet.andNot(
      tSCALADOC_WIKI_SYNTAX,
      TokenSet.create(
        ScalaDocTokenType.DOC_LINK_TAG,
        ScalaDocTokenType.DOC_LINK_CLOSE_TAG,
        ScalaDocTokenType.DOC_HTTP_LINK_TAG,
        ScalaDocTokenType.DOC_INNER_CODE_TAG,
        ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG
      )
    )
  }

  initAttributes()

  private def initAttributes(): Unit = {
    val helper = new ScalaSyntaxHighlighter()
    import helper.fillMap

    fillMap(ATTRIBUTES, tLINE_COMMENTS, DefaultHighlighter.LINE_COMMENT)
    fillMap(ATTRIBUTES, tBLOCK_COMMENTS, DefaultHighlighter.BLOCK_COMMENT)
    fillMap(ATTRIBUTES, tDOC_COMMENTS, DefaultHighlighter.DOC_COMMENT)
    fillMap(ATTRIBUTES, kRESERVED_WORDS, DefaultHighlighter.KEYWORD)
    fillMap(ATTRIBUTES, tNUMBERS, DefaultHighlighter.NUMBER)
    fillMap(ATTRIBUTES, tVALID_STRING_ESCAPE, DefaultHighlighter.VALID_STRING_ESCAPE)
    fillMap(ATTRIBUTES, tINVALID_CHARACTER_ESCAPE, DefaultHighlighter.INVALID_STRING_ESCAPE)
    fillMap(ATTRIBUTES, tINVALID_UNICODE_ESCAPE, DefaultHighlighter.INVALID_STRING_ESCAPE)
    fillMap(ATTRIBUTES, tSTRINGS, DefaultHighlighter.STRING)
    fillMap(ATTRIBUTES, tBRACES, DefaultHighlighter.BRACES)
    fillMap(ATTRIBUTES, tBRACKETS, DefaultHighlighter.BRACKETS)
    fillMap(ATTRIBUTES, tPARENTHESES, DefaultHighlighter.PARENTHESES)
    fillMap(ATTRIBUTES, tSEMICOLON, DefaultHighlighter.SEMICOLON)
    fillMap(ATTRIBUTES, tDOT, DefaultHighlighter.DOT)
    fillMap(ATTRIBUTES, tCOMMA, DefaultHighlighter.COMMA)

    fillMap(ATTRIBUTES, tOPS, DefaultHighlighter.ASSIGN)
    fillMap(ATTRIBUTES, tARROW, DefaultHighlighter.ARROW)
    fillMap(ATTRIBUTES, tCOMMENT_TAGS, DefaultHighlighter.SCALA_DOC_TAG)
    fillMap(ATTRIBUTES, TokenSet.orSet(
      TokenSet.andNot(
        ScalaDocTokenType.ALL_SCALADOC_TOKENS,
        tCOMMENT_TAGS
      ),
      TokenSet.create(
        ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER,
        ScalaDocTokenType.DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT
      )
    ), DefaultHighlighter.DOC_COMMENT)
    fillMap(ATTRIBUTES, tSCALADOC_HTML_TAGS, DefaultHighlighter.SCALA_DOC_HTML_TAG)
    fillMap(ATTRIBUTES, tSCALADOC_WIKI_SYNTAX, DefaultHighlighter.SCALA_DOC_WIKI_SYNTAX)
    fillMap(ATTRIBUTES, tSCALADOC_HTML_ESCAPE, DefaultHighlighter.SCALA_DOC_HTML_ESCAPE)

    fillMap(ATTRIBUTES0, tXML_TAG, DefaultHighlighter.XML_TAG)
    fillMap(ATTRIBUTES0, tXML_TAG_NAME, DefaultHighlighter.XML_TAG)
    fillMap(ATTRIBUTES0, tXML_ATTRIBUTE_NAME, DefaultHighlighter.XML_TAG)
    fillMap(ATTRIBUTES0, tXML_ATTRIBUTE_VALUE, DefaultHighlighter.XML_TAG)

    fillMap(ATTRIBUTES, tXML_TAG_NAME, DefaultHighlighter.XML_TAG_NAME)
    fillMap(ATTRIBUTES, tXML_TAG_DATA, DefaultHighlighter.XML_TAG_DATA)
    fillMap(ATTRIBUTES, tXML_ATTRIBUTE_NAME, DefaultHighlighter.XML_ATTRIBUTE_NAME)
    fillMap(ATTRIBUTES, tXML_ATTRIBUTE_VALUE, DefaultHighlighter.XML_ATTRIBUTE_VALUE)
    fillMap(ATTRIBUTES, tXML_COMMENT, DefaultHighlighter.XML_COMMENT)

    fillMap(ATTRIBUTES, tDOC_TAG_PARAM, DefaultHighlighter.SCALA_DOC_TAG_PARAM_VALUE)
    fillMap(ATTRIBUTES, tINTERPOLATED_STRINGS, DefaultHighlighter.INTERPOLATED_STRING_INJECTION)
  }

}