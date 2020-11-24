package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.QualId
import org.jetbrains.plugins.scala.lang.parser.util.InScala3
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.parseRuleInBlockOrIndentationRegion

/** 
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

/*
 * Packaging := 'package' QualId [nl] '{' TopStatSeq '}'
 */
object Packaging extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder):Boolean = {
    val packMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kPACKAGE =>
        builder.advanceLexer() //Ate package
        if (!(QualId())) {
          packMarker.drop()
          return false
        }
        //parsing body of regular packaging
        val (blockIndentation, baseIndentation) = builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE =>
            if (builder.twoNewlinesBeforeCurrentToken) {
              builder error ScalaBundle.message("lbrace.expected")
              packMarker.done(ScalaElementType.PACKAGING)
              return true
            }
            builder.advanceLexer() //Ate '{'
            BlockIndentation.create -> None
          case InScala3(ScalaTokenTypes.tCOLON) =>
            if (builder.twoNewlinesBeforeCurrentToken) {
              builder error ScalaBundle.message("lbrace.or.colon.expected")
              packMarker.done(ScalaElementType.PACKAGING)
              return true
            }
            builder.advanceLexer() // Ate :
            val currentIndent = builder.currentIndentationWidth
            builder.findPreviousIndent match {
              case indentO@Some(indent) if indent > currentIndent =>
                BlockIndentation.noBlock -> indentO
              case _ =>
                packMarker.done(ScalaElementType.PACKAGING)
                return true
            }
          case _ =>
            builder error ScalaBundle.message("lbrace.expected")
            packMarker.done(ScalaElementType.PACKAGING)
            return true
        }

        builder.enableNewlines()
        builder.maybeWithIndentationWidth(baseIndentation) {
          parseRuleInBlockOrIndentationRegion(blockIndentation, baseIndentation, ErrMsg("def.dcl.expected")) {
            TopStatSeq(baseIndent = baseIndentation)
            true
          }
        }
        blockIndentation.drop()
        builder.restoreNewlinesState()
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