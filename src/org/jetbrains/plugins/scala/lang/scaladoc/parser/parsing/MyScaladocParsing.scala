package org.jetbrains.plugins.scala
package lang
package scaladoc
package parser
package parsing

import com.intellij.lang.PsiBuilder
import ScalaDocElementTypes._
import lexer.docsyntax.ScaladocSyntaxElementType
import lexer.ScalaDocTokenType._
import com.intellij.psi.tree.IElementType

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
      case a: ScaladocSyntaxElementType =>
        parseWikiSyntax
        hasClosingElementsInWikiSyntax = false
      case DOC_INLINE_TAG_START =>
        isInInlinedTag = true
        parseTag
      case DOC_TAG_NAME =>
        parseTag
      case DOC_INNER_CODE_TAG =>
        if (builder.getTokenText == "}}}") {
          builder.error("Closing code tag before opening one")
          builder.advanceLexer()
        } else {
          parseInnerCode
        }
      case _ =>
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

    if (tokenText == "]]" || tokenText == "}}}") {
      builder.error("Closing tag before opening one")
      marker.done(tokenType)
      return false
    } else if (tokenType == DOC_HEADER) {
      marker.drop()
      return false
    }

    while (!isEndOfComment) {
      if ((builder.getTokenType != DOC_WHITESPACE) && (builder.getTokenType != DOC_COMMENT_LEADING_ASTERISKS)) {
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
        case a: ScaladocSyntaxElementType =>
          if (tokenType == a) {
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
        case DOC_WHITESPACE =>
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
    
    while (!isEndOfComment && builder.getTokenType != DOC_INNER_CODE_TAG) {
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
    import MyScaladocParsing._

    val marker = builder.mark
    if (isInInlinedTag) {
      ParserUtils.getToken(builder, DOC_INLINE_TAG_START)
    }

    assert(builder.getTokenType eq DOC_TAG_NAME, builder.getTokenText + "  " + builder.getTokenType + "  " + builder.getCurrentOffset)
    val tagName = builder.getTokenText
    if (!isEndOfComment) {
      builder.advanceLexer()
    } else {
      builder.error("Unexpected end of tag body")
    }
    
    if (isInInlinedTag) {
      builder.error("Inline tag")
    } else {                //todo
      tagName match {
        case THROWS_TAG | PARAM_TAG | TYPE_PARAM_TAG =>
          if (!ParserUtils.lookAhead(builder, builder.getTokenType, DOC_TAG_VALUE_TOKEN)) builder.error("Missing tag param")
        case SEE_TAG | AUTHOR_TAG | NOTE_TAG | RETURN_TAG | DEFINE_TAG | SINCE_TAG | VERSION_TAG =>
          //do nothing
        case _ =>
          builder.error("unknown tag")
      }
    }
    
    while (!isEndOfComment(builder)) {
      if (isInInlinedTag) {
        val tokenType = builder.getTokenType
        builder.advanceLexer()
        tokenType match {
          case DOC_INLINE_TAG_END =>
            marker.done(DOC_INLINED_TAG)
            isInInlinedTag = false
            return true
          case _ => // do nothing
        }
      } else if (DOC_TAG_NAME eq builder.getTokenType) {
        marker.done(DOC_TAG)
        return true
      } else if (builder.getTokenType.isInstanceOf[ScaladocSyntaxElementType]) {
        parseWikiSyntax
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
  private val PARAM_TAG = "@param"
  private val TYPE_PARAM_TAG = "@tparam"
  private val THROWS_TAG = "@throws"

  private val SEE_TAG = "@see"
  private val AUTHOR_TAG = "@author"
  private val NOTE_TAG = "@note"
  private val RETURN_TAG = "@return"
  private val SINCE_TAG = "@since"
  private val DEFINE_TAG = "@define"
  private val VERSION_TAG = "@version"

  private val INLINE_LINK_TAG = "@link"
  private val INLINE_LITERAL_TAG = "@literal"
  private val INLINE_CODE_TAG = "@code"
}
