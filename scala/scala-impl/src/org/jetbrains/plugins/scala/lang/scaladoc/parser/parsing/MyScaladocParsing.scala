package org.jetbrains.plugins.scala
package lang
package scaladoc
package parser
package parsing

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilderImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes._

import scala.collection.immutable.HashMap

/**
 * User: Dmitry Naidanov
 * Date: 11/12/11
 */

class MyScaladocParsing(private val psiBuilder: PsiBuilder) extends ScalaDocElementTypes {
  private var isInInlinedTag: Boolean = false
  private var hasClosingElementsInWikiSyntax: Boolean = false
  private var canHaveTags = true
  private var flags = 0

  private def setFlag(flag: Int) {
    flags |= flag
  }

  private def isSetFlag(flag: Int): Boolean = (flags & flag) != 0

  private def clearFlag(flag: Int) {
    flags &= ~flag
  }

  private def isEndOfComment(implicit builder: PsiBuilder): Boolean = 
    builder.eof() || builder.getTokenType == DOC_COMMENT_END

  def parse() {
    while (parseCommentData(psiBuilder)) { }
    while (!psiBuilder.eof()) {
      psiBuilder.advanceLexer()
    }
  }
  
  private def parseCommentData(implicit builder: PsiBuilder): Boolean = {
    if (isEndOfComment) return false
    
    builder.getTokenType match {
      case DOC_LINK_CLOSE_TAG =>
        builder.error("Closing link tag before opening")
        builder.advanceLexer()
      case DOC_INNER_CLOSE_CODE_TAG =>
        builder.error("Closing code tag before opening")
        builder.advanceLexer()
      case _: ScaladocSyntaxElementType =>
        parseWikiSyntax
        hasClosingElementsInWikiSyntax = false
      case DOC_INLINE_TAG_START =>
        isInInlinedTag = true
        parseTag
      case DOC_TAG_NAME =>
        parseTag
      case DOC_INNER_CODE_TAG =>
        parseInnerCode
      case DOC_COMMENT_DATA | DOC_COMMENT_BAD_CHARACTER | DOC_WHITESPACE | DOC_COMMENT_LEADING_ASTERISKS |
           DOC_COMMENT_START | DOC_COMMENT_END | DOC_MACROS =>
        builder.advanceLexer()
      case badToken @ _ =>
        System.out.println("Error: " + badToken)
        builder.advanceLexer()
    }
    
    true
  }

  private def parseWikiSyntax(implicit builder: PsiBuilder): Boolean = {
    if (!builder.getTokenType.isInstanceOf[ScaladocSyntaxElementType]) {
      return false
    }
    val tokenType = builder.getTokenType.asInstanceOf[ScaladocSyntaxElementType]
    val tokenText = builder.getTokenText
    val marker = builder.mark()
    setFlag(tokenType.getFlagConst)
    if (!isEndOfComment) {
      builder.advanceLexer()
    }

    def closedBy(message: String = "new paragraph") {
      marker.done(tokenType)
      builder.error("Wiki syntax element closed by " + message)
      clearFlag(tokenType.getFlagConst)
    }

    def canClose(element: IElementType): Boolean = {
      element != null &&
      (element == tokenType ||
              ((tokenType == DOC_LINK_TAG) || (tokenType == DOC_HTTP_LINK_TAG)) && element == DOC_LINK_CLOSE_TAG)
    }
    
    if (tokenType == DOC_HEADER) {
      marker.drop()
      return false
    }
    
    if (tokenType == DOC_LINK_TAG && builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && !isEndOfComment) {
      StableId.parse(new ScalaPsiBuilderImpl(builder), true, DOC_CODE_LINK_VALUE)
    }

    while (!isEndOfComment) {
      if (!(builder.getTokenType == DOC_WHITESPACE && builder.getTokenText.contains("\n")) &&
              builder.getTokenType != DOC_COMMENT_LEADING_ASTERISKS) {
        hasClosingElementsInWikiSyntax = false
      }
      builder.getTokenType match {
        case VALID_DOC_HEADER =>
          if (tokenType != VALID_DOC_HEADER) {
            canHaveTags = false
            parseWikiSyntax
          } else {
            if (tokenText.length > builder.getTokenText.length) {
              builder.error("Header closed by opening new one")
            } else {
              builder.advanceLexer()
            }

            canHaveTags = true
            marker.done(tokenType)
            return true
          }
        case DOC_HEADER =>
          val headerEnd = builder.getTokenText
          builder.advanceLexer()
          if (tokenType == VALID_DOC_HEADER && tokenText.length <= headerEnd.length) {
            canHaveTags = true
            marker.done(tokenType)
            return true
          }
        case DOC_LINK_CLOSE_TAG =>
          builder.advanceLexer()
          if (tokenType == DOC_LINK_TAG || tokenType == DOC_HTTP_LINK_TAG) {
            marker.done(tokenType)
            return true
          } else {
            builder.error("Closing link element before opening one")
          }
        case a: ScaladocSyntaxElementType if a == DOC_MONOSPACE_TAG || tokenType != DOC_MONOSPACE_TAG =>
          if (tokenType == a) { //
            builder.advanceLexer()
            clearFlag(tokenType.getFlagConst)
            marker.done(tokenType)
            return true
          } else if (isSetFlag(a.getFlagConst)) {
            builder.advanceLexer()
            builder.error("Cross tags")
          } else {
            parseWikiSyntax
            if (hasClosingElementsInWikiSyntax) {
              closedBy()
              return true
            }
          }
        case DOC_INNER_CODE_TAG if canHaveTags =>
          closedBy("Inner code tag")
          return true
        case DOC_INLINE_TAG_START
          if ParserUtils.lookAhead(builder, DOC_INLINE_TAG_START, DOC_TAG_NAME) && canHaveTags =>
          isInInlinedTag = true
          parseTag
        case DOC_WHITESPACE if tokenType != DOC_MONOSPACE_TAG =>
          if (tokenType == DOC_LINK_TAG) {
            marker.done(DOC_LINK_TAG)
            return true
          }
          if (!hasClosingElementsInWikiSyntax &&
                  (builder.getTokenText.indexOf("\n") == builder.getTokenText.lastIndexOf("\n"))) { //check is it single nl
            hasClosingElementsInWikiSyntax = true
            builder.advanceLexer()
          } else {
            hasClosingElementsInWikiSyntax = true //if nl was not single
            closedBy()
            return true
          }
        case DOC_TAG_NAME if canHaveTags =>
          hasClosingElementsInWikiSyntax = true
          closedBy("tag")
          return true
        case _ => builder.advanceLexer()
      }
    }
    
    if (!canClose(builder.getTokenType)) {
      builder.error("No closing element")
    }
    marker.done(tokenType)
    true
  }

  private def parseInnerCode(implicit builder: PsiBuilder): Boolean = {
    val marker = builder.mark()
    if (isEndOfComment) {
      builder.error("Unclosed code tag")
      marker.done(DOC_INNER_CODE_TAG)
      return true
    }
    builder.advanceLexer()
    
    while (!isEndOfComment && builder.getTokenType != DOC_INNER_CLOSE_CODE_TAG) {
      builder.advanceLexer()
    }
    if (isEndOfComment) {
      builder.error("Unclosed code tag")
    } else {
      builder.advanceLexer()
    }
    marker.done(DOC_INNER_CODE_TAG)

    true
  }

  private def parseTag(implicit builder: PsiBuilder): Boolean = {
    import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing._

    val marker = builder.mark()
    if (isInInlinedTag) ParserUtils.getToken(builder, DOC_INLINE_TAG_START)

    assert(builder.getTokenType eq DOC_TAG_NAME, builder.getTokenText + "  "
            + builder.getTokenType + "  " + builder.getCurrentOffset)

    val tagName = builder.getTokenText
    if (!isEndOfComment) builder.advanceLexer() else builder.error("Unexpected end of tag body")
    
    if (isInInlinedTag) builder.error("Inline tag") else {
      tagName match {
        case THROWS_TAG => 
          if (!isEndOfComment) {
            builder.advanceLexer()
          }
          StableId.parse(new ScalaPsiBuilderImpl(builder), true, DOC_TAG_VALUE_TOKEN)
        case PARAM_TAG | TYPE_PARAM_TAG | DEFINE_TAG =>
          if (!ParserUtils.lookAhead(builder, builder.getTokenType, DOC_TAG_VALUE_TOKEN)) builder.error("Missing tag param")
        case tag if allTags.contains(tag) =>
          //do nothing
        case _ =>
          builder.error("unknown tag")
      }
    }
    
    
    while (!isEndOfComment(builder)) {
      if (isInInlinedTag) {
        val tokenType = builder.getTokenType
        tokenType match {
          case DOC_INLINE_TAG_END =>
            builder.advanceLexer()
            marker.done(DOC_INLINED_TAG)
            isInInlinedTag = false
            return true
          case DOC_TAG_VALUE_TOKEN =>
            val valueMarker = builder.mark()
            builder.advanceLexer()
            valueMarker.done(DOC_TAG_VALUE_TOKEN)
          case _ =>
            builder.advanceLexer()
        }
      } else if (DOC_TAG_NAME eq builder.getTokenType) {
        marker.done(DOC_TAG)
        return true
      } else if (builder.getTokenType.isInstanceOf[ScaladocSyntaxElementType]) {
        parseWikiSyntax
      } else if (builder.getTokenType == DOC_TAG_VALUE_TOKEN) {
        val tagValMarker = builder.mark()
        builder.advanceLexer()
        tagValMarker.done(DOC_TAG_VALUE_TOKEN)
      } else {
        builder.advanceLexer()
      }
    }
    marker.done(if (isInInlinedTag) DOC_INLINED_TAG else DOC_TAG)
    isInInlinedTag = false
    
    true
  }
}

object MyScaladocParsing {
  val PARAM_TAG = "@param"
  val TYPE_PARAM_TAG = "@tparam"
  val THROWS_TAG = "@throws"

  val SEE_TAG = "@see"
  val AUTHOR_TAG = "@author"
  val NOTE_TAG = "@note"
  val RETURN_TAG = "@return"
  val SINCE_TAG = "@since"
  val DEFINE_TAG = "@define"
  val VERSION_TAG = "@version"
  val TODO_TAG = "@todo"
  val USECASE_TAG = "@usecase"
  val EXAMPLE_TAG = "@example"
  val DEPRECATED_TAG = "@deprecated"
  val MIGRATION_TAG = "@migration"
  val INHERITDOC_TAG = "@inheritdoc"

  val GROUP_TAG = "@group"
  val GROUP_NAME_TAG = "@groupname"
  val GROUP_DESC_TAG = "@groupdesc"
  val GROUP_PRIO_TAG = "@groupprio"
  val CONSTRUCTOR_TAG ="@constructor"
  

  val escapeSequencesForWiki = HashMap[String, String]("`" -> "&#96;", "^" -> "&#94;", "__" -> "&#95;&#95;",
    "'''" -> "&#39;&#39;&#39;", "''" -> "&#39;&#39;", ",," -> "&#44;&#44;", "[[" -> "&#91;&#91;", "=" -> "&#61;")

  val allTags = Set(PARAM_TAG, TYPE_PARAM_TAG, THROWS_TAG, SEE_TAG, AUTHOR_TAG, NOTE_TAG, RETURN_TAG, SINCE_TAG,
    DEFINE_TAG, VERSION_TAG, TODO_TAG, USECASE_TAG, EXAMPLE_TAG, DEPRECATED_TAG, MIGRATION_TAG, GROUP_TAG, 
    GROUP_NAME_TAG, GROUP_DESC_TAG, GROUP_PRIO_TAG, CONSTRUCTOR_TAG, INHERITDOC_TAG)
  val tagsWithParameters = Set(PARAM_TAG, TYPE_PARAM_TAG, THROWS_TAG)
}
