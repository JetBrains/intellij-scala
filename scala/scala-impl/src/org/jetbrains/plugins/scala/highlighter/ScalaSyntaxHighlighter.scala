package org.jetbrains.plugins.scala
package highlighter

import java.{util => ju}

import org.jetbrains.plugins.scala.lang.TokenSets.TokenSetExt
import com.intellij.lexer._
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.{SyntaxHighlighter, SyntaxHighlighterBase}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.{StringEscapesTokenTypes, TokenType}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes, ScalaXmlLexer, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes

final class ScalaSyntaxHighlighter(
  scalaLexer: Lexer,
  scalaDocHighlighter: SyntaxHighlighter,
  htmlHighlighter: SyntaxHighlighter
) extends SyntaxHighlighterBase {

  import ScalaSyntaxHighlighter._

  override def getHighlightingLexer: LayeredLexer =
    new CompoundLexer(
      scalaLexer,
      scalaDocHighlighter.getHighlightingLexer,
      htmlHighlighter.getHighlightingLexer
    )

  override def getTokenHighlights(elementType: IElementType): Array[TextAttributesKey] =
    SyntaxHighlighterBase.pack(
      Attributes0.get(elementType).orNull,
      Attributes.get(elementType).orNull
    )
}

object ScalaSyntaxHighlighter {

  import ScalaDocElementTypes.SCALA_DOC_COMMENT
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
  private val tSCALADOC_XML_TAGS = TokenSet.create(
    XML_TAG_NAME,
    XML_START_TAG_START,
    XML_EMPTY_ELEMENT_END,
    XML_END_TAG_START,
    XML_TAG_END
  )

  //ScalaDoc Wiki syntax elements
  // TODO: headers are excluded cause:
  //  1) inner headers are parsed wrongly e.g. ===header = content===, `=` is also parsed as header instead of content
  //  2) current headers using <h1>, <h2> tags are not rendered as headers by IDEA
  private val tSCALADOC_WIKI_SYNTAX: TokenSet =
    ALL_SCALADOC_SYNTAX_ELEMENTS -- (ScalaDocTokenType.VALID_DOC_HEADER, ScalaDocTokenType.DOC_HEADER)

  //for value in @param value
  private val tDOC_TAG_VALUE = TokenSet.create(
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
  private val tDOC_COMMENT_TAGS = TokenSet.create(
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
      TokenSet.create(SCALA_DOC_COMMENT) -> DOC_COMMENT,
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

      tXML_TAG_NAME -> XML_TAG_NAME,
      tXML_TAG_DATA -> XML_TAG_DATA,
      tXML_ATTRIBUTE_NAME -> XML_ATTRIBUTE_NAME,
      tXML_ATTRIBUTE_VALUE -> XML_ATTRIBUTE_VALUE,
      tXML_COMMENT -> XML_COMMENT,

      tDOC_TAG_VALUE -> SCALA_DOC_TAG_PARAM_VALUE,
      tDOC_COMMENT_TAGS -> SCALA_DOC_TAG,
      (tSCALADOC_XML_TAGS -- tXML_TAG_NAME) -> SCALA_DOC_HTML_TAG,
      tSCALADOC_WIKI_SYNTAX -> SCALA_DOC_WIKI_SYNTAX,
      tSCALADOC_HTML_ESCAPE -> SCALA_DOC_HTML_ESCAPE,
      TokenSet.create(DOC_LIST_ITEM_HEAD) -> SCALA_DOC_LIST_ITEM_HEAD,

      ((ALL_SCALADOC_TOKENS ++ (DOC_COMMENT_BAD_CHARACTER, DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT))
        -- tDOC_TAG_VALUE
        -- tDOC_COMMENT_TAGS
        -- tSCALADOC_XML_TAGS
        -- tSCALADOC_WIKI_SYNTAX
        -- tSCALADOC_HTML_ESCAPE
        -- DOC_LIST_ITEM_HEAD
        ) -> DOC_COMMENT,

      tINTERPOLATED_STRINGS -> INTERPOLATED_STRING_INJECTION
    )
  }

  private def attributesMap(attributes: (TokenSet, TextAttributesKey)*): Map[IElementType, TextAttributesKey] = {
    val elementTypesAttributes: Seq[(IElementType, TextAttributesKey)] = for {
      (tokenSet, key) <- attributes
      typ  <- tokenSet.getTypes
    } yield typ -> key

    val (unique, nonUnique) = elementTypesAttributes.groupBy(_._1).mapValues(_.map(_._2).distinct).partition(_._2.size == 1)
    if (nonUnique.nonEmpty) {
      val nonUniqueTexts = nonUnique.map { case (token, attributes) => s"element type: $token, attributes: ${attributes.mkString(", ")}"}
      val message = s"Tree element types were registered multiple times with different attributes:\n${nonUniqueTexts.mkString("\n")}}"
      throw new AssertionError(message)
    }
    unique.mapValues(_.head)
  }

  private class CompoundLexer(
    scalaLexer: Lexer,
    scalaDocLexer: Lexer,
    htmlLexer: Lexer
  ) extends LayeredLexer(scalaLexer) {

    init()

    private def init(): Unit = {
      registerSelfStoppingLayer(
        new StringLiteralLexer('\"', tSTRING),
        Array(tSTRING),
        IElementType.EMPTY_ARRAY
      )

      registerSelfStoppingLayer(
        new StringLiteralLexer('\'', tSTRING),
        Array(tCHAR),
        IElementType.EMPTY_ARRAY
      )

      //interpolated string highlighting
      registerLayer(
        new LayeredLexer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tINTERPOLATED_STRING)),
        tINTERPOLATED_STRING
      )

      //scalaDoc highlighting
      val scalaDocLayer = new LayeredLexer(new ScalaDocLexerHighlightingWrapper(scalaDocLexer))
      scalaDocLayer.registerLayer(
        new ScalaHtmlHighlightingLexerWrapper(htmlLexer),
        DOC_COMMENT_DATA
      )
      registerSelfStoppingLayer(
        scalaDocLayer,
        Array[IElementType](SCALA_DOC_COMMENT),
        IElementType.EMPTY_ARRAY
      )
    }
  }

  private[highlighter] class CustomScalaLexer(delegate: ScalaLexer)
    extends DelegateLexer(delegate) {

    private var openingTags = new ju.Stack[String]
    private var tagMatch = false
    private var isInClosingTag = false
    private var afterStartTagStart = false
    private var nameIndex = 0

    override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
      super.start(buffer, startOffset, endOffset, initialState) // TODO is it correct???

      // TODO State class
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
        if (tSCALADOC_XML_TAGS.contains(elementType)) tXMLTAGPART
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

  private class ScalaHtmlHighlightingLexerWrapper(delegate: Lexer) extends DelegateLexer(delegate) {

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

  private class ScalaDocLexerHighlightingWrapper(delegate: Lexer) extends DelegateLexer(delegate) {

    import ScalaDocLexerHighlightingWrapper._

    private val elements = new ju.Stack[IElementType]

    override def getTokenType: IElementType = super.getTokenType match {
      case `tDOT` | `tIDENTIFIER` => DOC_COMMENT_DATA
      case _ if isOnTop(DOC_COMMON_CLOSE_WIKI_TAG) => DOC_COMMON_CLOSE_WIKI_TAG
      case tokenType => tokenType
    }

    override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
      elements.clear()
      super.start(buffer, startOffset, endOffset, initialState)
    }

    override def advance(): Unit = {
      super.advance()

      if (isOnTop(DOC_COMMON_CLOSE_WIKI_TAG)) {
        elements.pop()
        elements.pop() //will never be empty there
      }

      super.getTokenType match {
        case tokenType if SyntaxToSwap.contains(tokenType) =>
          val item = if (isOnTop(tokenType))
            DOC_COMMON_CLOSE_WIKI_TAG
          else
            tokenType

          elements.push(item)
        case _ =>
      }
    }

    private def isOnTop(tokenType: IElementType) =
      !elements.isEmpty && (elements.peek eq tokenType)
  }

  private object ScalaDocLexerHighlightingWrapper {

    private val SyntaxToSwap = TokenSet.andNot(
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