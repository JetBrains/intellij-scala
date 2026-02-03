package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.parser

import com.intellij.lang.{LightPsiParser, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.ScalaDocRefLinkElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.ScalaDocRefLinkElementTypes.STRICT_MEMBER_ID

class ScalaDocRefLinkParser extends PsiParser with LightPsiParser {

  override def parseLight(rootElementType: IElementType, builder: PsiBuilder): Unit = {
    val rootMarker = builder.mark()
    parseQuery(builder)
    rootMarker.done(rootElementType)
  }

  override def parse(rootElementType: IElementType, builder: PsiBuilder): com.intellij.lang.ASTNode = {
    parseLight(rootElementType, builder)
    builder.getTreeBuilt
  }

  /**
   * Query ::= '#' Identifier
   *         | SegmentedQuery
   */
  private def parseQuery(builder: PsiBuilder): Unit = {
    if (builder.getTokenType == tINNER_CLASS) {
      val marker = builder.mark()
      // StrictMemberId: '#' followed by identifier
      builder.advanceLexer() // consume '#'
      if (builder.getTokenType == tIDENTIFIER) {
        builder.advanceLexer() // consume identifier
      } else {
        builder.error(ScalaBundle.message("identifier.expected"))
      }
      marker.done(STRICT_MEMBER_ID)
    } else {
      parseSegmentedQuery(builder)
    }

    builder.getTokenType match {
      case null | ScalaTokenTypes.tLSQBRACKET | ScalaTokenTypes.tLPARENTHESIS =>
        // this is fine
      case _ =>
        builder.error("Expected '.', '#', '(', '[' or whitespace")
    }

    // rest is just unused tokens
    while (builder.getTokenType != null) {
      builder.advanceLexer()
    }
  }

  /**
   * SegmentedQuery ::= Qualifier ( ('.' | '#') Qualifier )*
   */
  private def parseSegmentedQuery(builder: PsiBuilder): Unit = {
    var marker = builder.mark()
    parseQualifier(builder)
    marker.done(ScalaDocRefLinkElementTypes.QUERY_SEGMENT)

    // Check for type parameters '(' or '[' that end the query
    while (builder.getTokenType == tDOT || builder.getTokenType == tINNER_CLASS) {
      builder.advanceLexer() // consume '.' or '#'
      parseQualifier(builder)
      marker = marker.precede()
      marker.done(ScalaDocRefLinkElementTypes.QUERY_SEGMENT)
    }
  }

  /**
   * Qualifier ::= 'this' | 'package' | Identifier
   */
  private def parseQualifier(builder: PsiBuilder): Unit = {
    builder.getTokenType match {
      case `kTHIS` | `kPACKAGE` | `tIDENTIFIER` =>
        builder.advanceLexer()
      case _ =>
        builder.error(ScalaBundle.message("identifier.this.or.package.expected"))
    }
  }
}
