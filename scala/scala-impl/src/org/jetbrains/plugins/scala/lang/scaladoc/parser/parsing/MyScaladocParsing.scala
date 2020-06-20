package org.jetbrains.plugins.scala
package lang
package scaladoc
package parser
package parsing

import java.util

import com.intellij.application.options.CodeStyle
import com.intellij.lang.{ASTNode, PsiBuilder}
import com.intellij.lexer.HtmlLexer
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.xml.XmlTokenType
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.TokenSets.TokenSetExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.PsiBuilderExt
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilderImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes._
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing._
import org.jetbrains.plugins.scala.util.IndentUtil

import scala.collection.immutable.HashMap

// TODO: looks like plenty of complex logic in ScalaDoc parsing is only required during
//  "Quick Info" generation. For example: Paragraphs parsing, Lists parsing, Html tags tracking
//  I am not sure, but maybe it's worth to do this parsing only on info rendering?
//  But first do some performance test and decide whether it worth it.
final class MyScaladocParsing(private val builder: PsiBuilder) extends ScalaDocElementTypes {

  private var hasClosingElementsInWikiSyntax: Boolean = false
  private var canHaveTags = true
  private var flags = 0

  private var hasLineBreak: Boolean = false
  private var hasSomeData: Boolean = false

  private val htmlTagsTracker = new HtmlTagsTracker

  private def setFlag(flag: Int): Unit =
    flags |= flag

  private def isSetFlag(flag: Int): Boolean =
    flag != 0 && (flags & flag) != 0

  private def clearFlag(flag: Int): Unit =
    flags &= ~flag

  def parse(root: IElementType): Unit = {
    val rootMarker = builder.mark

    /**
     * TODO: This is a very dirty hack.
     *  For some reason Play2Template comments are parsed as an ordinary ScalaDoc comment.
     *  [[org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes#SCALA_DOC_COMMENT]]
     *  with all inner token types as DOC_COMMENT_BAD_CHARACTER.
     *  Play2Templates should have a dedicated PSI element for a template comment with own parsing rules (simple, though)
     *  NOTE: this was so before implementing ScalaDoc paragraphs, I've just added more assertions...)
     */
    val isPlay2TemplateComment = builder.getTokenType != DOC_COMMENT_BAD_CHARACTER
    if (isPlay2TemplateComment) {
      assert(builder.getTokenType == DOC_COMMENT_START)
      builder.advanceLexer()
      updateLineBreakFlagAfterAsterisks()

      parseDescription()
      parseTags()
    }

    while (!builder.eof())
      builder.advanceLexer()

    rootMarker.done(root)
  }

  private def isEndOfComment: Boolean =
    builder.eof() || builder.getTokenType == DOC_COMMENT_END

  private def isNewLine: Boolean =
    builder.getTokenType == DOC_WHITESPACE && builder.getTokenText.contains("\n")

  private def isBlankLine: Boolean =
    isNewLine && (builder.lookAhead(1) match {
      case DOC_COMMENT_LEADING_ASTERISKS | DOC_WHITESPACE => true
      case _                                              => false
    })

  private def parseDescription(): Unit = {
    consumeBlankLines()
    while (!isEndOfComment && !isTagStart) {
      parseDescriptionPart()
      consumeBlankLines()
    }
  }

  private def parseDescriptionPart(): Unit = {
    if (isListItemStart)
      parseList()
    else
      parseParagraph()
    hasSomeData = false
  }

  private def parseParagraph(): Unit = {
    val marker = builder.mark()

    var continue = true
    while (!isEndOfComment && continue) {
      parseCommentData()
      consumeBlankLines()
      if (isTagStart)
        continue = false
      if ((hasLineBreak || isListItemStart) && !htmlTagsTracker.isInsideTag)
        continue = false
    }

    if (hasSomeData)
      marker.done(DOC_PARAGRAPH)
    else
      marker.drop()
  }

  // NOTE: it will parse ok if only spaces are used in lists OR if only tabs are used,
  // but it will not parse ok if the list contains mixed leading spaces with tabs e.g.
  private val tabSize: Int = {
    val indentOptions = CodeStyle.getSettings(builder.getProject).getLanguageIndentOptions(ScalaLanguage.INSTANCE)
    indentOptions.TAB_SIZE
  }
  private def calcIndent(builder: PsiBuilder): Int =
    IndentUtil.calcIndent(builder.getTokenText, tabSize)

  private def parseList(): Unit = {
    assert(isListItemStart)

    val firstItemIndent = calcIndent(builder)
    val marker = builder.mark()
    builder.advanceLexer() // ate space
    val firstItemStyle = builder.getTokenText

    parseListItem(firstItemIndent)

    var continue = true
    while (!isEndOfComment && continue && isListItemStart) {
      val wsMarker = builder.mark()
      val indent = calcIndent(builder)
      builder.advanceLexer() // ate space
      val style = builder.getTokenText

      if (style == firstItemStyle) {
        if (indent == firstItemIndent) {
          wsMarker.drop()
          parseListItem(firstItemIndent)
        } else {
          wsMarker.rollbackTo()
          continue = false
        }
      } else {
        wsMarker.rollbackTo()
        continue = false
      }
    }

    marker.done(DOC_LIST)
  }

  private def isListItemStart: Boolean =
    builder.lookAhead(DOC_WHITESPACE, DOC_LIST_ITEM_HEAD)

  private def parseListItem(currentItemIndent: Int): Unit = {
    val marker = builder.mark()
    builder.advanceLexer() // ate list item head
    builder.advanceLexer() // ate space after

    var continue = true
    while (!isEndOfComment && continue) {
      parseCommentData()
      consumeBlankLines()

      while (isListItemStart && continue) {
        val isNestedList = calcIndent(builder) > currentItemIndent
        if (isNestedList)
          parseList()
        else
          continue = false
      }

      if (hasLineBreak || isTagStart)
        continue = false
    }

    marker.done(DOC_LIST_ITEM)
  }

  private def consumeBlankLines(): Unit = {
    if (isBlankLine) {
      builder.advanceLexer()
      builder.advanceLexer()
    }
    while (isBlankLine) {
      builder.advanceLexer()
      builder.advanceLexer()
      hasLineBreak = true
    }
  }

  private def isTagStart: Boolean =
    builder.lookAhead(DOC_TAG_NAME) || builder.lookAhead(DOC_WHITESPACE, DOC_TAG_NAME)

  private def parseTags(): Unit =
    while (isTagStart && parseTag()) {}

  private def updateLineBreakFlagAfterAsterisks(): Unit =
    if (isNewLine)
      hasLineBreak = true

  private def parseCommentData(): Unit = {
    val tokenType = builder.getTokenType
    tokenType match {
      case DOC_LINK_CLOSE_TAG =>
        scaladocError(builder, ScalaBundle.message("scaladoc.parsing.closing.link.tag.before.opening"))
        builder.advanceLexer()
      case DOC_INNER_CLOSE_CODE_TAG =>
        scaladocError(builder, ScalaBundle.message("scaladoc.parsing.closing.code.tag.before.opening"))
        builder.advanceLexer()
      case DOC_INNER_CODE_TAG =>
        parseInnerCode()
      case DOC_INLINE_TAG_START =>
        parseInlineTag()
      case DOC_COMMENT_LEADING_ASTERISKS =>
        builder.advanceLexer()
      case DOC_COMMENT_DATA =>
        htmlTagsTracker.update(builder.getTokenText)
        builder.advanceLexer()
      case DOC_WHITESPACE |
           DOC_COMMENT_END |
           DOC_MACROS |
           DOC_COMMENT_BAD_CHARACTER =>
        builder.advanceLexer()
      case _: ScalaDocSyntaxElementType => // will be handle later
      case badToken @ _ =>
        System.out.println(ScalaBundle.message("scaladoc.parsing.error.bad.token", badToken))
        builder.advanceLexer()
    }

    tokenType match {
      case DOC_COMMENT_LEADING_ASTERISKS =>
        updateLineBreakFlagAfterAsterisks()
      case _: ScalaDocSyntaxElementType =>
        hasLineBreak = false
        parseWikiSyntax() // updates hasLineBreak inside
        hasClosingElementsInWikiSyntax = false
      case _ =>
        hasLineBreak = false
    }

    tokenType match {
      case DOC_COMMENT_LEADING_ASTERISKS | DOC_WHITESPACE |
           DOC_COMMENT_START | DOC_COMMENT_END =>
      case _ =>
        hasSomeData = true
    }
  }

  private def parseWikiSyntax(): Boolean = {
    val syntaxElementType: ScalaDocSyntaxElementType = builder.getTokenType match {
      case syntax: ScalaDocSyntaxElementType => syntax
      case _                                 => return false
    }

    val tokenText = builder.getTokenText
    val marker = builder.mark()

    setFlag(syntaxElementType.getFlagConst)
    if (!isEndOfComment)
      builder.advanceLexer()

    def closedByNewParagraph(): Unit =
      closedBy(ScalaBundle.message("scaladoc.parsing.wiki.syntax.closed.by.new.paragraph"))

    def closedBy(@Nls message: String): Unit = {
      marker.done(syntaxElementType)
      scaladocError(builder, ScalaBundle.message("scaladoc.parsing.wiki.syntax.element.closed.by.message", message))
      clearFlag(syntaxElementType.getFlagConst)
    }

    def canClose(element: IElementType): Boolean = {
      element != null &&
        (element == syntaxElementType ||
          ((syntaxElementType == DOC_LINK_TAG) || (syntaxElementType == DOC_HTTP_LINK_TAG)) && element == DOC_LINK_CLOSE_TAG)
    }

    def parseUntilAndConvertToData(set: TokenSet): Unit = {
      while (!isEndOfComment && !set.contains(builder.getTokenType)) {
        val tt = builder.getTokenType

        if (!set.contains(tt))
          builder.remapCurrentToken(DOC_COMMENT_DATA)
        if (!set.contains(tt))
          builder.advanceLexer()
      }

      if (isEndOfComment)
        builder.error(ScalaBundle.message("scaladoc.parsing.open.syntax.element"))
      else
        builder.advanceLexer()
    }

    syntaxElementType match {
      case DOC_HEADER =>
        marker.drop()
        return false
      case DOC_LINK_TAG =>
        if (builder.getTokenType == DOC_WHITESPACE)
          builder.advanceLexer()
        if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && !isEndOfComment) {
          val psiBuilder = new ScalaPsiBuilderImpl(builder, isScala3 = false)
          StableId.parse(psiBuilder, forImport = true, DOC_CODE_LINK_VALUE)
        }
      case DOC_MONOSPACE_TAG =>
        parseUntilAndConvertToData(monospaceEndTokenSet)
        marker.done(syntaxElementType)
        return true
      case _ =>
    }

    while (!isEndOfComment) {
      val tokenType = builder.getTokenType

      val isLeadingAsterisks = tokenType == DOC_COMMENT_LEADING_ASTERISKS
      if (isNewLine || isLeadingAsterisks) {
        //skip
      } else {
        hasClosingElementsInWikiSyntax = false
      }

      tokenType match {
        case VALID_DOC_HEADER =>
          if (syntaxElementType != VALID_DOC_HEADER) {
            canHaveTags = false
            parseWikiSyntax()
          } else {
            if (tokenText.length > builder.getTokenText.length) {
              scaladocError(builder, ScalaBundle.message("scaladoc.parsing.header.closed.by.opening.new.one"))
            } else {
              builder.advanceLexer()
            }

            canHaveTags = true
            marker.done(syntaxElementType)
            return true
          }
        case DOC_HEADER =>
          val headerEnd = builder.getTokenText
          builder.advanceLexer()
          if (syntaxElementType == VALID_DOC_HEADER && tokenText.length <= headerEnd.length) {
            canHaveTags = true
            marker.done(syntaxElementType)
            return true
          }
        case DOC_LINK_CLOSE_TAG =>
          builder.advanceLexer()
          if (syntaxElementType == DOC_LINK_TAG || syntaxElementType == DOC_HTTP_LINK_TAG) {
            clearFlag(syntaxElementType.getFlagConst)
            marker.done(syntaxElementType)
            return true
          } else {
            scaladocError(builder, ScalaBundle.message("scaladoc.parsing.closing.link.element.before.opening.one"))
          }
        case a: ScalaDocSyntaxElementType if a == DOC_MONOSPACE_TAG || syntaxElementType != DOC_MONOSPACE_TAG =>
          if (syntaxElementType == a) { //
            builder.advanceLexer()
            clearFlag(syntaxElementType.getFlagConst)
            marker.done(syntaxElementType)
            return true
          } else if (isSetFlag(a.getFlagConst)) {
            builder.advanceLexer()
            scaladocError(builder, ScalaBundle.message("scaladoc.parsing.cross.tags"))
          } else {
            parseWikiSyntax()
            if (hasClosingElementsInWikiSyntax) {
              closedByNewParagraph()
              return true
            }
          }
        case DOC_INNER_CODE_TAG if canHaveTags =>
          closedBy(ScalaBundle.message("scaladoc.parsing.wiki.syntax.closed.by.inner.code.tag"))
          return true
        case DOC_INLINE_TAG_START
          if builder.lookAhead(1, DOC_TAG_NAME) && canHaveTags =>

          parseInlineTag()
        case DOC_WHITESPACE if syntaxElementType != DOC_MONOSPACE_TAG =>
          if (!hasClosingElementsInWikiSyntax && hasSingleNewLine(builder.getTokenText)) {
            hasClosingElementsInWikiSyntax = true
            builder.advanceLexer()
          } else {
            hasClosingElementsInWikiSyntax = true //if nl was not single
            closedByNewParagraph()
            return true
          }
        case DOC_TAG_NAME if canHaveTags =>
          hasClosingElementsInWikiSyntax = true
          closedBy(ScalaBundle.message("scaladoc.parsing.wiki.syntax.closed.by.tag"))
          return true
        case DOC_COMMENT_LEADING_ASTERISKS =>
          builder.advanceLexer()
          updateLineBreakFlagAfterAsterisks()
          if (isNewLine && syntaxElementType != DOC_MONOSPACE_TAG) {
            closedByNewParagraph()
            return true
          }
        case DOC_COMMENT_DATA =>
          htmlTagsTracker.update(builder.getTokenText)
          builder.advanceLexer()
        case _ =>
          builder.advanceLexer()
      }

      hasLineBreak = false
    }

    if (!canClose(builder.getTokenType)) {
      scaladocError(builder, ScalaBundle.message("scaladoc.parsing.no.closing.element"))
    }
    marker.done(syntaxElementType)
    true
  }

  private def hasSingleNewLine(text: String) =
    text.indexOf("\n") == text.lastIndexOf("\n")

  private def parseInnerCode(): Boolean = {
    val marker = builder.mark()
    if (isEndOfComment) {
      scaladocError(builder, ScalaBundle.message("scaladoc.parsing.unclosed.code.tag"))
      marker.done(DOC_INNER_CODE_TAG)
      return true
    }
    builder.advanceLexer()

    while (!isEndOfComment && builder.getTokenType != DOC_INNER_CLOSE_CODE_TAG) {
      builder.advanceLexer()
    }
    if (isEndOfComment) {
      scaladocError(builder, ScalaBundle.message("scaladoc.parsing.unclosed.code.tag"))
    } else {
      builder.advanceLexer()
    }
    marker.done(DOC_INNER_CODE_TAG)

    true
  }

  private def parseInlineTag(): Boolean = {
    val marker = builder.mark()
    if (builder.getTokenType == DOC_INLINE_TAG_START)
      builder.advanceLexer()
    else
      return false

    val tagName = builder.getTokenText
    assert(builder.getTokenType == DOC_TAG_NAME, s"$tagName  ${builder.getTokenType}  ${builder.getCurrentOffset}")

    if (!isEndOfComment)
      builder.advanceLexer()
    else
      scaladocError(builder, ScalaBundle.message("scaladoc.parsing.unexpected.end.of.tag.body"))

    scaladocError(builder, ScalaBundle.message("scaladoc.parsing.inline.tag"))
    tagName match {
      case JAVA_LINK_TAG | JAVA_LINK_PLAIN_TAG =>
        if (!isEndOfComment && builder.getTokenType == ScalaDocTokenType.DOC_WHITESPACE)
          builder.advanceLexer()

        val psiBuilder = new ScalaPsiBuilderImpl(builder, isScala3 = false)
        StableId.parse(psiBuilder, forImport = true, DOC_TAG_VALUE_TOKEN)
      case _ => // do nothing
    }

    var continue = true
    while (!isEndOfComment && continue)
      builder.getTokenType match {
        case DOC_INLINE_TAG_END  => builder.advanceLexer(); continue = false
        case DOC_TAG_VALUE_TOKEN => parseTagValue()
        case _                   => builder.advanceLexer()
      }

    marker.done(DOC_INLINED_TAG)

    true
  }

  private def parseTagValue(): Unit = {
    assert(builder.getTokenType == DOC_TAG_VALUE_TOKEN)

    val valueMarker = builder.mark()
    builder.advanceLexer()
    valueMarker.done(DOC_TAG_VALUE_TOKEN)
  }

  private def parseTag(): Boolean = {
    val marker = builder.mark()

    if (builder.getTokenType == DOC_WHITESPACE)
      builder.advanceLexer()

    val tagName = builder.getTokenText
    assert(builder.getTokenType == DOC_TAG_NAME, s"$tagName  ${builder.getTokenType}  ${builder.getCurrentOffset}")

    if (!isEndOfComment)
      builder.advanceLexer()
    else
      scaladocError(builder, ScalaBundle.message("scaladoc.parsing.unexpected.end.of.tag.body"))

    tagName match {
      case THROWS_TAG =>
        if (!isEndOfComment) {
          builder.advanceLexer()
        }
        val psiBuilder = new ScalaPsiBuilderImpl(builder, isScala3 = false)
        StableId.parse(psiBuilder, forImport = true, DOC_TAG_VALUE_TOKEN)
      case PARAM_TAG | TYPE_PARAM_TAG | DEFINE_TAG =>
        if (!builder.lookAhead(builder.getTokenType, DOC_TAG_VALUE_TOKEN))
          scaladocError(builder, ScalaBundle.message("scaladoc.parsing.missing.tag.param"))
      case tag if allTags.contains(tag) => //do nothing
      case _ =>
        scaladocError(builder, ScalaBundle.message("scaladoc.parsing.unknown.tag", tagName))
    }

    while (!isEndOfComment && !isTagStart)
      builder.getTokenType match {
        case DOC_TAG_NAME                 => // stop
        case _: ScalaDocSyntaxElementType => parseWikiSyntax()
        case DOC_TAG_VALUE_TOKEN          => parseTagValue()
        case _                            => builder.advanceLexer()
      }

    marker.done(DOC_TAG)

    true
  }

  private def scaladocError(builder: PsiBuilder, @Nls message: String): Unit =
    builder.error(message)
}

object MyScaladocParsing {
  val PARAM_TAG      = "@param"
  val TYPE_PARAM_TAG = "@tparam"
  val RETURN_TAG     = "@return"
  val THROWS_TAG     = "@throws"

  val SEE_TAG        = "@see"
  val AUTHOR_TAG     = "@author"
  val NOTE_TAG       = "@note"
  val SINCE_TAG      = "@since"
  val DEFINE_TAG     = "@define"
  val VERSION_TAG    = "@version"
  val TODO_TAG       = "@todo"
  val USECASE_TAG    = "@usecase"
  val EXAMPLE_TAG    = "@example"
  val DEPRECATED_TAG = "@deprecated"
  val MIGRATION_TAG  = "@migration"
  val INHERITDOC_TAG = "@inheritdoc"

  val GROUP_TAG       = "@group"
  val GROUP_NAME_TAG  = "@groupname"
  val GROUP_DESC_TAG  = "@groupdesc"
  val GROUP_PRIO_TAG  = "@groupprio"
  val CONSTRUCTOR_TAG = "@constructor"

  val JAVA_LINK_TAG       = "@link"
  val JAVA_LINK_PLAIN_TAG = "@linkplain"

  val escapeSequencesForWiki: Map[String, String] = HashMap[String, String](
    "`"   -> "&#96;",
    "^"   -> "&#94;",
    "__"  -> "&#95;&#95;",
    "'''" -> "&#39;&#39;&#39;",
    "''"  -> "&#39;&#39;",
    ",,"  -> "&#44;&#44;",
    "[["  -> "&#91;&#91;",
    "="   -> "&#61;"
  )

  val allTags = Set(
    PARAM_TAG, TYPE_PARAM_TAG, THROWS_TAG, SEE_TAG, AUTHOR_TAG, NOTE_TAG, RETURN_TAG, SINCE_TAG,
    DEFINE_TAG, VERSION_TAG, TODO_TAG, USECASE_TAG, EXAMPLE_TAG, DEPRECATED_TAG, MIGRATION_TAG, GROUP_TAG,
    GROUP_NAME_TAG, GROUP_DESC_TAG, GROUP_PRIO_TAG, CONSTRUCTOR_TAG, INHERITDOC_TAG
  )
  val tagsWithParameters = Set(
    DEFINE_TAG, PARAM_TAG, TYPE_PARAM_TAG, THROWS_TAG
  )

  private val nonDataTokens: TokenSet = TokenSet.create(
    DOC_COMMENT_END, DOC_WHITESPACE, DOC_COMMENT_LEADING_ASTERISKS
  )

  private val monospaceEndTokenSet: TokenSet =
    MyScaladocParsing.nonDataTokens + DOC_MONOSPACE_TAG

  /**
   * In general we want to treat blank lines as a paragraph separator e.g:
   * {{{
   * /**
   *  * paragraph 1
   *  *
   *  * paragraph 2
   *  */
   * }}}
   *
   * But if a user explicitly uses html opening/close tags we do not want to break paragraph on a line break, e.g.:
   * {{{
   *  /**
   *  * <p>paragraph 1
   *  *
   *  * still paragraph 1<p>
   *  * <b> bold text
   *  *
   *  * still same bold text</b>
   *  */
   * }}}
   *
   * To achieve this behaviour we keep track of all html open/close tags with a stack.
   * Currently ScalaDoc lexer treats all html tags as a plain [[ScalaDocTokenType.DOC_COMMENT_DATA]] and doesn't involve
   * html tags parsing. So to detect tags we use a separate html lexer here.
   *
   * NOTE: it doesn't work properly with tags without a closing tag e.g.
   * {{{
   * /**
   *  * <p>paragraph 1
   *  * <p>paragraph 2
   *  */
   * }}}
   */
  private class HtmlTagsTracker {

    import HtmlTagsTracker.SelfClosingTagNames

    private val htmlLexer = new HtmlLexer()
    private val htmlTagsStack = new util.Stack[String]

    def isInsideTag: Boolean =
      !htmlTagsStack.isEmpty

    def update(text: CharSequence): Unit = {
      // optimization, not to tokenize input if we 100% sure that it doesn't contain any html tags
      if (!StringUtils.contains(text, '<')) return

      htmlLexer.start(text)

      while (htmlLexer.getTokenType != null) {
        val tokenType = htmlLexer.getTokenType
        tokenType match {
          case XmlTokenType.XML_START_TAG_START  =>
            htmlLexer.advance()
            if (htmlLexer.getTokenType == XmlTokenType.XML_NAME) {
              val tagName = htmlLexer.getTokenText
              htmlLexer.advance()
              while(htmlLexer.getTokenType == XmlTokenType.XML_WHITE_SPACE)
                htmlLexer.advance()

              val isSelfClosed = SelfClosingTagNames.contains(tagName) || // e.g. <br>
                htmlLexer.getTokenType == XmlTokenType.XML_EMPTY_ELEMENT_END // e.g. <p/>
              if (!isSelfClosed)
                htmlTagsStack.push(tagName)
            }

          case XmlTokenType.XML_END_TAG_START =>
            htmlLexer.advance()
            if (htmlLexer.getTokenType == XmlTokenType.XML_NAME) {
              val tagName = htmlLexer.getTokenText
              if (htmlTagsStack.peek() == tagName)
                htmlTagsStack.pop()
              htmlLexer.advance()
            }
          case _ =>
            htmlLexer.advance()
        }
      }
    }
  }

  private object HtmlTagsTracker {
    private val SelfClosingTagNames = Set(
      "area", "base", "br", "col", "embed", "hr", "img", "input",
      "link", "meta", "param", "source", "track", "wbr",
    )
  }
}
