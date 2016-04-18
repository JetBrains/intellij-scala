package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Qual_Id
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/** 
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

/*
 * Packaging := 'package' QualId [nl] '{' TopStatSeq '}'
 */
object Packaging extends Packaging {
  override protected val topStatSeq = TopStatSeq
}

trait Packaging {
  protected val topStatSeq: TopStatSeq

  def parse(builder: ScalaPsiBuilder):Boolean = {
    val packMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kPACKAGE =>
        builder.advanceLexer() //Ate package
        if (!(Qual_Id parse builder)) {
          packMarker.drop()
          return false
        }
        //parsing body of regular packaging
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            if (builder.twoNewlinesBeforeCurrentToken) {
              builder error ScalaBundle.message("lbrace.expected")
              packMarker.done(ScalaElementTypes.PACKAGING)
              return true
            }
            builder.advanceLexer() //Ate '{'
            builder.enableNewlines
            ParserUtils.parseLoopUntilRBrace(builder, () => {
              //parse packaging body
              topStatSeq parse(builder, true)
            })
            builder.restoreNewlinesState
            packMarker.done(ScalaElementTypes.PACKAGING)
            true
          }
          case _ => {
            builder error ScalaBundle.message("lbrace.expected")
            packMarker.done(ScalaElementTypes.PACKAGING)
            true
          }
        }
      case _ =>
        //this code shouldn't be reachable, if it is, this is unexpected error
        builder error ScalaBundle.message("unreachable.error")
        packMarker.drop()
        false
    }
  }
}