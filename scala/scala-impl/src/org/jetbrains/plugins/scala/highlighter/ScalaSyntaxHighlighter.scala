package org.jetbrains.plugins.scala.highlighter

import com.intellij.lexer._
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.{SyntaxHighlighter, SyntaxHighlighterBase}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.{StringEscapesTokenTypes, TokenType}
import org.jetbrains.plugins.scala.Tracing
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter.CustomScalaLexer
import org.jetbrains.plugins.scala.highlighter.lexer.{ScalaInterpolatedStringLiteralLexer, ScalaMultilineStringLiteralLexer, ScalaStringLiteralLexer}
import org.jetbrains.plugins.scala.lang.TokenSets.TokenSetExt
import org.jetbrains.plugins.scala.lang.lexer.{ScalaKeywordTokenType, ScalaLexer, ScalaTokenType, ScalaTokenTypes, ScalaXmlLexer, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes
import org.jetbrains.plugins.scalaDirective.lang.parser.ScalaDirectiveElementTypes

import java.{util => ju}

// todo: move to some inner package
// TODO something is wrong with the highlighter, see comment in SCL-18701
/**
 * TODO: Extract scaladoc highlighter completely,
 *  now it's something intermediate in
 *  [[org.jetbrains.plugins.scala.highlighter]]
 *  and
 *  [[org.jetbrains.plugins.scalaDoc.highlighter]]
 *  packages
 *
 * @see [[org.jetbrains.plugins.scala.highlighter.ScalaColorSchemeAnnotator]]
 * @see [[org.jetbrains.plugins.scala.codeInsight.daemon.ScalaRainbowVisitor]]
 */
final class ScalaSyntaxHighlighter(
  scalaLexer: CustomScalaLexer,
  scalaDocHighlighter: SyntaxHighlighter,
  scalaDirectiveHighlighter: SyntaxHighlighter,
  htmlHighlighter: SyntaxHighlighter,
) extends SyntaxHighlighterBase {

  import ScalaSyntaxHighlighter._

  override def getHighlightingLexer: LayeredLexer =
    new CompoundLexer(
      scalaLexer,
      scalaDocHighlighter.getHighlightingLexer,
      scalaDirectiveHighlighter.getHighlightingLexer,
      htmlHighlighter.getHighlightingLexer,
    )

  override def getTokenHighlights(elementType: IElementType): Array[TextAttributesKey] = elementType match {
    case CLASS_IDENTIFIER => Array(DefaultHighlighter.CLASS)
    case TRAIT_IDENTIFIER => Array(DefaultHighlighter.TRAIT)
    case OBJECT_IDENTIFIER => Array(DefaultHighlighter.OBJECT)
    case ENUM_IDENTIFIER => Array(DefaultHighlighter.ENUM)
    case GIVEN_IDENTIFIER => Array(DefaultHighlighter.GIVEN)
    case METHOD_IDENTIFIER => Array(DefaultHighlighter.METHOD_DECLARATION)
    case VALUE_IDENTIFIER => Array(DefaultHighlighter.LOCAL_VARIABLES)
    case VARIABLE_IDENTIFIER => Array(DefaultHighlighter.LOCAL_VARIABLES)
    case TYPE_IDENTIFIER => Array(DefaultHighlighter.TYPE_ALIAS)
    case _ =>
      SyntaxHighlighterBase.pack(
        Attributes0.get(elementType).orNull,
        Attributes.get(elementType).orNull
      )
  }
}

object ScalaSyntaxHighlighter {

  private val CLASS_IDENTIFIER = new ScalaTokenType("class identifier")
  private val TRAIT_IDENTIFIER = new ScalaTokenType("trait identifier")
  private val OBJECT_IDENTIFIER = new ScalaTokenType("object identifier")
  private val ENUM_IDENTIFIER = new ScalaTokenType("enum identifier")
  private val GIVEN_IDENTIFIER = new ScalaTokenType("given identifier")
  private val METHOD_IDENTIFIER = new ScalaTokenType("method identifier")
  private val VALUE_IDENTIFIER = new ScalaTokenType("value identifier")
  private val VARIABLE_IDENTIFIER = new ScalaTokenType("variable identifier")
  private val TYPE_IDENTIFIER = new ScalaTokenType("type identifier")

  import ScalaDirectiveTokenTypes._
  import ScalaDocElementTypes.SCALA_DOC_COMMENT
  import ScalaDocTokenType._
  import ScalaTokenTypes._
  import ScalaXmlTokenTypes._
  // Comments
  private val tLINE_COMMENTS = TokenSet.create(tLINE_COMMENT)

  private val tBLOCK_COMMENTS = TokenSet.create(
    tBLOCK_COMMENT,
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
      SOFT_KEYWORDS -> KEYWORD,
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

      tINTERPOLATED_STRINGS -> INTERPOLATED_STRING_INJECTION,

      TokenSet.create(tDIRECTIVE_PREFIX) -> SCALA_DIRECTIVE_PREFIX,
      TokenSet.create(tDIRECTIVE_COMMAND) -> SCALA_DIRECTIVE_COMMAND,
      TokenSet.create(tDIRECTIVE_KEY) -> SCALA_DIRECTIVE_KEY,
      TokenSet.create(tDIRECTIVE_VALUE) -> SCALA_DIRECTIVE_VALUE
    )
  }

  private def attributesMap(attributes: (TokenSet, TextAttributesKey)*): Map[IElementType, TextAttributesKey] = {
    val elementTypesAttributes: Seq[(IElementType, TextAttributesKey)] = for {
      (tokenSet, key) <- attributes
      typ  <- tokenSet.getTypes
    } yield typ -> key

    val (unique, nonUnique) = elementTypesAttributes.groupBy(_._1).view.mapValues(_.map(_._2).distinct).toMap.partition(_._2.size == 1)
    if (nonUnique.nonEmpty) {
      val nonUniqueTexts = nonUnique.map { case (token, attributes) => s"element type: $token, attributes: ${attributes.mkString(", ")}"}
      val message = s"Tree element types were registered multiple times with different attributes:\n${nonUniqueTexts.mkString("\n")}}"
      throw new AssertionError(message)
    }
    unique.view.mapValues(_.head).toMap
  }


  private val softKeywords: Map[String, ScalaKeywordTokenType] =
    ScalaTokenTypes.SOFT_KEYWORDS.getTypes.iterator
      .map(_.asInstanceOf[ScalaKeywordTokenType])
      .map(tokenType => tokenType.keywordText -> tokenType)
      .toMap

  private class CompoundLexer(
    scalaLexer: CustomScalaLexer,
    scalaDocLexer: Lexer,
    scalaDirectiveLexer: Lexer,
    htmlLexer: Lexer,
  ) extends com.intellij.lexer.LayeredLexer(scalaLexer) {

    init()

    private def init(): Unit = {
      val noUnicodeEscapesInRawStrings = scalaLexer.noUnicodeEscapesInRawStrings
      //char literal highlighting
      registerSelfStoppingLayer(
        new ScalaStringLiteralLexer('\'', tSTRING),
        Array(tCHAR),
        IElementType.EMPTY_ARRAY
      )

      //string highlighting
      registerSelfStoppingLayer(
        new ScalaStringLiteralLexer('\"', tSTRING),
        Array(tSTRING),
        IElementType.EMPTY_ARRAY
      )
      //multiline string highlighting
      registerSelfStoppingLayer(
        new ScalaMultilineStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tMULTILINE_STRING, noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings),
        Array(tMULTILINE_STRING),
        IElementType.EMPTY_ARRAY
      )

      //interpolated string highlighting
      registerLayer(
        new LayeredLexer(new ScalaInterpolatedStringLiteralLexer(
          StringLiteralLexer.NO_QUOTE_CHAR,
          tINTERPOLATED_STRING,
          isRawLiteral = false,
          isMultiline = false,
          noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings,
        )),
        tINTERPOLATED_STRING
      )
      registerLayer(
        new LayeredLexer(new ScalaInterpolatedStringLiteralLexer(
          StringLiteralLexer.NO_QUOTE_CHAR,
          tINTERPOLATED_STRING,
          isRawLiteral = true,
          isMultiline = false,
          noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings,
        )),
        tINTERPOLATED_RAW_STRING
      )

      //multiline interpolated string highlighting
      registerLayer(
        new LayeredLexer(new ScalaInterpolatedStringLiteralLexer(
          StringLiteralLexer.NO_QUOTE_CHAR,
          tINTERPOLATED_MULTILINE_STRING,
          isRawLiteral = false,
          isMultiline = true,
          noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings,
        )),
        tINTERPOLATED_MULTILINE_STRING
      )
      registerLayer(
        new LayeredLexer(new ScalaInterpolatedStringLiteralLexer(
          StringLiteralLexer.NO_QUOTE_CHAR,
          tINTERPOLATED_MULTILINE_STRING,
          isRawLiteral = true,
          isMultiline = true,
          noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings,
        )),
        tINTERPOLATED_MULTILINE_RAW_STRING
      )

      registerSelfStoppingLayer(scalaDirectiveLexer, Array(ScalaDirectiveElementTypes.SCALA_DIRECTIVE), IElementType.EMPTY_ARRAY)

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

  private[highlighter] class CustomScalaLexer(
    delegate: ScalaLexer,
    val isScala3: Boolean,
    val noUnicodeEscapesInRawStrings: Boolean
  ) extends DelegateLexer(delegate) {

    private var openingTags = new ju.Stack[String]
    private var tagMatch = false
    private var isInClosingTag = false
    private var afterStartTagStart = false
    private var nameIndex = 0

    override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
      Tracing.highlightingLexerStart(buffer, startOffset, endOffset, initialState)

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
      case ScalaTokenTypes.tIDENTIFIER =>
        if (isScala3) {
          softKeywords.get(getTokenText) match {
            case Some(softKeyword) if checkSoftKeywordHeuristics(softKeyword) => softKeyword
            case _ => identifier()
          }
        } else {
          identifier()
        }
      case elementType =>
        if (tSCALADOC_XML_TAGS.contains(elementType)) tXMLTAGPART
        else elementType
    }

    private def identifier(): IElementType =
      if (after("class")) CLASS_IDENTIFIER
      else if (after("trait")) TRAIT_IDENTIFIER
      else if (after("object")) OBJECT_IDENTIFIER
      else if (after("enum")) ENUM_IDENTIFIER
      else if (after("def")) METHOD_IDENTIFIER
      else if (after("val")) VALUE_IDENTIFIER
      else if (after("var")) VARIABLE_IDENTIFIER
      else if (after("type")) TYPE_IDENTIFIER
      else ScalaTokenTypes.tIDENTIFIER

    private def after(s: String): Boolean = {
      val text = getBufferSequence
      var i = getTokenStart - 1
      while (i >= 0 && text.charAt(i).isWhitespace) { i -= 1 }
      i += 1
      i >= s.length && text.subSequence(i - s.length, i) == s && (i == s.length || {
        val charBefore = text.charAt(i - s.length - 1)
        charBefore.isWhitespace || charBefore == ';' || charBefore == '}'
      })
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

    private def checkSoftKeywordHeuristics(tokenType: ScalaKeywordTokenType): Boolean = {
      // https://docs.scala-lang.org/scala3/reference/soft-modifier.html
      val text = getBufferSequence
      val start = getTokenStart
      val end = getTokenEnd

      def checkPred(intermediate: Char => Boolean, pred: Char => Boolean, default: Boolean): Boolean =
        checkBuffer(intermediate, pred, default, start - 1, -1)

      def checkFollowing(intermediate: Char => Boolean, pred: Char => Boolean, default: Boolean): Boolean =
        checkBuffer(intermediate, pred, default, end, 1)

      tokenType match {
        case ScalaTokenType.EndKeyword =>
          // check that it's preceded by a newline
          checkPred(_.isWhitespace, _ == '\n', default = true)
        case ScalaTokenType.UsingKeyword =>
          // check that it's preceded by an opening parenthesis and followed by an identifier
          checkPred(_.isWhitespace, _ == '(', default = false) &&
            checkFollowing(_.isWhitespace, _.isUnicodeIdentifierStart, default = false)

        case ScalaTokenType.ExtensionKeyword =>
          // check that it's followed by an opening parenthesis or opening bracket
          checkFollowing(_.isWhitespace, "([".contains(_), default = false)

        case ScalaTokenType.DerivesKeyword =>
          // check that it's followed by an identifier
          checkFollowing(_.isWhitespace, _.isUnicodeIdentifierStart, default = false)

        case ScalaTokenType.AsKeyword =>
          checkPred(_.isWhitespace, _.isUnicodeIdentifierPart, default = false) &&
            checkFollowing(_.isWhitespace, _.isUnicodeIdentifierStart, default = false)

        case ScalaTokenType.OpenKeyword | ScalaTokenType.InfixKeyword | ScalaTokenType.InlineKeyword |
             ScalaTokenType.OpaqueKeyword | ScalaTokenType.TransparentKeyword =>
          // soft modifiers
          var i = end
          var needWs = true
          while (i < text.length) {
            val c = text.charAt(i)

            if (c.isWhitespace) {
              i += 1
              needWs = false
            } else if (needWs) {
              return false
            } else {
              c match {
                case 'o' if checkBuffer(text, i, "open") => i += 4
                case 'i' if checkBuffer(text, i, "infix") => i += 5
                case 'i' if checkBuffer(text, i, "inline") => i += 6
                case 'o' if checkBuffer(text, i, "opaque") => i += 6
                case 't' if checkBuffer(text, i, "transparent") => i += 11
                case 'c' if checkBuffer(text, i, "case") => i += 4

                case 'd' if checkBuffer(text, i, "def") => return true
                case 'v' if checkBuffer(text, i, "val") => return true
                case 'v' if checkBuffer(text, i, "var") => return true
                case 't' if checkBuffer(text, i, "type") => return true
                case 'g' if checkBuffer(text, i, "given") => return true
                case 'c' if checkBuffer(text, i, "class") => return true
                case 't' if checkBuffer(text, i, "trait") => return true
                case 'o' if checkBuffer(text, i, "object") => return true
                case 'e' if checkBuffer(text, i, "enum") => return true
                case _ => return false
              }
              needWs = true
            }
          }
          false
        case _ =>
          false
      }
    }

    private def checkBuffer(intermediate: Char => Boolean, pred: Char => Boolean, default: Boolean, start: Int, dir: Int): Boolean = {
      val text = getBufferSequence
      var i = start
      while (i >= 0 && i < text.length) {
        val c = text.charAt(i)
        if (pred(c)) {
          return true
        } else if (!intermediate(c)) {
          return false
        } else {
          i += dir
        }
      }
      default
    }

    private def checkBuffer(text: CharSequence, start: Int, string: String): Boolean = {
      var textIdx = start
      var stringIdx = 0
      while (textIdx < text.length && stringIdx < string.length) {
        if (text.charAt(textIdx) != string.charAt(stringIdx)) {
          return false
        }
        textIdx += 1
        stringIdx += 1
      }
      stringIdx == string.length
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