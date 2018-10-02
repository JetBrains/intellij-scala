package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.template

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SelfType

import scala.annotation.tailrec

/** 
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 *  TemplateBody ::= '{' [SelfType] TemplateStat {semi TemplateStat} '}'
 */
object TemplateBody {

  def parse(builder: ScalaPsiBuilder) {
    val templateBodyMarker = builder.mark
    //Look for {
    builder.enableNewlines()
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
      case _ => builder error ScalaBundle.message("lbrace.expected")
    }
    SelfType parse builder
    //this metod parse recursively TemplateStat {semi TemplateStat}
    @tailrec
    def subparse(): Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE =>
          builder.advanceLexer() //Ate }
          true
        case null =>
          builder error ScalaBundle.message("rbrace.expected")
          true
        case _ =>
          if (TemplateStat parse builder) {
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE =>
                builder.advanceLexer() //Ate }
                true
              case ScalaTokenTypes.tSEMICOLON =>
                while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) builder.advanceLexer()
                subparse()
              case _ =>
                if (builder.newlineBeforeCurrentToken) subparse()
                else {
                  builder error ScalaBundle.message("semi.expected")
                  builder.advanceLexer() //Ate something
                  subparse()
                }
            }
          }
          else {
            builder error ScalaBundle.message("def.dcl.expected")
            builder.advanceLexer() //Ate something
            subparse()
          }
      }
    }
    subparse()
    builder.restoreNewlinesState()
    templateBodyMarker.done(ScalaElementTypes.TEMPLATE_BODY)
  }
}