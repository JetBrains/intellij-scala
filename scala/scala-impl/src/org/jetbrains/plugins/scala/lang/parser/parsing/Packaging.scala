package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.End
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.QualId
import org.jetbrains.plugins.scala.lang.parser.util.InScala3
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.parseRuleInBlockOrIndentationRegion
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

/*
 * Packaging := 'package' QualId [nl] '{' TopStatSeq '}'
 */
object Packaging extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val packMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kPACKAGE =>
        builder.advanceLexer() //Ate package
        if (!QualId()) {
          packMarker.drop()
          return false
        }
        //parsing body of regular packaging
        val region = builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE =>
            if (!builder.twoNewlinesBeforeCurrentToken) {
              builder.advanceLexer() //Ate '{'
            } else {
              //if there are two new lines after `package` then parse it as block expression
            }
            builder.newBracedIndentationRegionHere
          case InScala3(ScalaTokenTypes.tCOLON) =>
            if (!builder.isScala3IndentationBasedSyntaxEnabled) {
              // we add the error but continue to parse
              builder.error(ScalaBundle.message("lbrace.expected"))
            }

            builder.advanceLexer() // Ate :
            builder.newBracelessIndentationRegionHere match {
              case Some(region) => region
              case None =>
                if (builder.hasPrecedingIndentation) {
                  builder error ErrMsg("indented.definitions.expected")
                } else {
                  builder error ErrMsg("expected.new.line.after.colon")
                }
                End()
                packMarker.done(ScalaElementType.PACKAGING)
                return true
            }
          case _ =>
            builder error ScalaBundle.message("lbrace.expected")
            packMarker.done(ScalaElementType.PACKAGING)
            return true
        }

        builder.withEnabledNewlines {
          builder.withIndentationRegion(region) {
            parseRuleInBlockOrIndentationRegion(region, ErrMsg("def.dcl.expected")) {
              TopStatSeq.parse(waitBrace = true)
              true
            }
          }
        }

        End()
        packMarker.done(ScalaElementType.PACKAGING)
        true
      case _ =>
        //this code shouldn't be reachable, if it is, this is unexpected error
        builder error ScalaBundle.message("unreachable.error")
        packMarker.drop()
        false
    }
  }
}