package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.{ExtensionKeyword, InlineKeyword}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{Param, ParamClause, TypeParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{FunDcl, FunDef}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.parseRuleInBlockOrIndentationRegion

import scala.annotation.tailrec

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

    val marker = builder.tryParseSoftKeywordWithRollbackMarker(ExtensionKeyword) match {
      case Some(marker) => marker
      case None => return false
    }

    if (!extensionDefFollows(builder.getTokenType)) {
      marker.rollbackTo()
      return false
    }

    TypeParamClause.parse(builder)
    ExtensionParameterClauses()
    ExtMethods()

    marker.done(ScalaElementType.EXTENSION)
    true
  }
}

// TODO: add annotator which will mark extensions without extension methods
object ExtMethods extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): true = {
    val extDefinitionsMarker = builder.mark()
    val (blockIndentation, baseIndentation, onlyOne) = builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE  =>
        builder.advanceLexer() // Ate {
        (BlockIndentation.create, None, false)
      case _ if builder.isScala3IndentationBasedSyntaxEnabled =>
        // TODO: colon is not available in extension methods
        //  we could still parse it and add an error in Annotator
        //  (it's likely that the error with : after `extension` will be quite frequent)
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
              extDefinitionsMarker.done(ScalaElementType.EXTENSION_BODY)
              return true
            }
          case None =>
            if (hasColon) {
              builder error ScalaBundle.message("expected.new.line.after.colon")
              End(builder.currentIndentationWidth)
              extDefinitionsMarker.done(ScalaElementType.EXTENSION_BODY)
              return true
            } else {
              (BlockIndentation.noBlock, None, true)
            }
        }
      case _ =>
        if (builder.findPreviousIndent.isEmpty) {
          (BlockIndentation.noBlock, None, true)
        }
        else {
          End(builder.currentIndentationWidth)
          extDefinitionsMarker.done(ScalaElementType.EXTENSION_BODY)
          return true
        }
//        End(builder.currentIndentationWidth)
//        extensionBodyMarker.done(ScalaElementType.TEMPLATE_BODY)
//        return true
    }

    if (onlyOne) ExtMethod()
    else builder.maybeWithIndentationWidth(baseIndentation) {
      parseRuleInBlockOrIndentationRegion(blockIndentation, baseIndentation, ErrMsg("extension.method.expected")) {
        ExtMethod()
      }
    }
    blockIndentation.drop()
    End(builder.currentIndentationWidth)
    extDefinitionsMarker.done(ScalaElementType.EXTENSION_BODY)
    true
  }
}

object ExtensionParameterClauses extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark
    //@TODO: leading using clauses
    if (!ExtensionParameterClause()) builder.error(ErrMsg("param.clause.expected"))

    while (ParamClause(mustBeUsing = true)) {}

    paramMarker.done(ScalaElementType.PARAM_CLAUSES)
    true
  }

  object ExtensionParameterClause extends ParsingRule {
    override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
      val paramMarker = builder.mark()
      if (builder.twoNewlinesBeforeCurrentToken) {
        paramMarker.drop()
        return false
      }

      builder.getTokenType match {
        case ScalaTokenTypes.tLPARENTHESIS =>
          builder.advanceLexer() //Ate (
          builder.disableNewlines()
        case _ =>
          paramMarker.rollbackTo()
          return false
      }

      builder.getTokenType match {
        case ScalaTokenTypes.kIMPLICIT =>
          builder.error(ScalaBundle.message("parameter.expected"))
          paramMarker.rollbackTo()
          builder.restoreNewlinesState()
          return false
        case ScalaTokenTypes.tIDENTIFIER if builder.getTokenText == "using" =>
          // ExtensionParameterClause can be preceded by a using class
          paramMarker.rollbackTo()
          builder.restoreNewlinesState()
          ParamClause()
          return ExtensionParameterClause()
        case _ => ()
      }

      if (!Param()) builder.error(ScalaBundle.message("parameter.expected"))

      builder.getTokenType match {
        case ScalaTokenTypes.tRPARENTHESIS =>
          builder.advanceLexer() //Ate )
        case _ =>
          builder.error(ScalaBundle.message("rparenthesis.expected"))
      }
      builder.restoreNewlinesState()
      paramMarker.done(ScalaElementType.PARAM_CLAUSE)
      true
    }
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