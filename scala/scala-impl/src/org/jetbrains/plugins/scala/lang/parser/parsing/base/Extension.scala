package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.{ExtensionKeyword, InlineKeyword}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ParamClauses, TypeParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{FunDcl, FunDef}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.parseRuleInBlockOrIndentationRegion

/*
 * Extension  ::=  ‘extension’ [DefTypeParamClause] ‘(’ DefParam ‘)’
 *                  {UsingParamClause} ExtMethods
 */
object Extension extends ParsingRule {
  private val extensionDefFollows = Set(ScalaTokenTypes.tLSQBRACKET, ScalaTokenTypes.tLPARENTHESIS)

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3) {
      return false
    }

    val iw = builder.currentIndentationWidth
    val marker = builder.tryParseSoftKeywordWithRollbackMarker(ExtensionKeyword) match {
      case Some(marker) => marker
      case None => return false
    }

    if (!extensionDefFollows(builder.getTokenType)) {
      marker.rollbackTo()
      return false
    }

    TypeParamClause.parse(builder)

    ParamClauses.parse(builder, expectAtLeastOneClause = true)

    ExtMethods()

    marker.done(ScalaElementType.Extension)
    true
  }
}


object ExtMethods extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): true = {
    val extensionBodyMarker = builder.mark()
    val (blockIndentation, baseIndentation, onlyOne) = builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE  =>
        builder.advanceLexer() // Ate {
        (BlockIndentation.create, None, false)
      case _ =>
        val hasColon = builder.getTokenType == ScalaTokenTypes.tCOLON
        if (hasColon) {
          builder.advanceLexer() // Ate :
        }

        builder.findPreviousIndent match {
          case indentO@Some(indent) =>
            if (indent > builder.currentIndentationWidth) {
              (BlockIndentation.noBlock, indentO, false)
            } else {
              builder error ErrMsg("expected.at.least.one.extension.method")
              End(builder.currentIndentationWidth)
              extensionBodyMarker.done(ScalaElementType.TEMPLATE_BODY)
              return true
            }
          case None =>
            if (hasColon) {
              builder error ScalaBundle.message("expected.new.line.after.colon")
              End(builder.currentIndentationWidth)
              extensionBodyMarker.done(ScalaElementType.TEMPLATE_BODY)
              return true
            } else {
              (BlockIndentation.noBlock, None, true)
            }
        }
    }

    if (onlyOne) ExtMethod()
    else builder.maybeWithIndentationWidth(baseIndentation) {
      parseRuleInBlockOrIndentationRegion(blockIndentation, baseIndentation, ErrMsg("extension.method.expected")) {
        ExtMethod()
      }
    }
    blockIndentation.drop()
    End(builder.currentIndentationWidth)
    extensionBodyMarker.done(ScalaElementType.TEMPLATE_BODY)
    true
  }
}

/*
 * ExtMethod ::=  {Annotation [nl]} {Modifier} ‘def’ DefDef
 */
object ExtMethod extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val defMarker = builder.mark
    defMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_COMMENTS_TOKEN, null)
    Annotations.parseAndBindToLeft()

    val modifierMarker = builder.mark

    def parseScalaMetaInline(): Boolean = builder.isMetaEnabled && builder.tryParseSoftKeyword(InlineKeyword)
    while (Modifier() || parseScalaMetaInline()) {}
    modifierMarker.done(ScalaElementType.MODIFIERS)

    val iw = builder.currentIndentationWidth
    if (FunDef() || FunDcl()) {
      End(iw)
      defMarker.done(ScalaElementType.FUNCTION_DEFINITION)
      true
    } else {
      defMarker.rollbackTo()
      false
    }
  }
}