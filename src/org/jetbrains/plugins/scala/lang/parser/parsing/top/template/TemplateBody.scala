package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.template

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SelfType
import org.jetbrains.plugins.scala.ScalaBundle
import builder.ScalaPsiBuilder
import annotation.tailrec

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
    builder.enableNewlines
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer //Ate {
      }
      case _ => builder error ScalaBundle.message("lbrace.expected")
    }
    SelfType parse builder
    //this metod parse recursively TemplateStat {semi TemplateStat}
    //@tailrec
    def subparse(): Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE => {
          builder.advanceLexer //Ate }
          return true
        }
        case null => {
          builder error ScalaBundle.message("rbrace.expected")
          return true
        }
        case _ => {
          if (TemplateStat parse builder) {
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => {
                builder.advanceLexer //Ate }
                return true
              }
              case ScalaTokenTypes.tSEMICOLON => {
                while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) builder.advanceLexer
                return subparse
              }
              case _ => {
                if (builder.newlineBeforeCurrentToken) return subparse
                builder error ScalaBundle.message("semi.expected")
                builder.advanceLexer //Ate something
                return subparse
              }
            }
          }
          else {
            builder error ScalaBundle.message("def.dcl.expected")
            builder.advanceLexer //Ate something
            return subparse
          }
        }
      }
    }
    subparse
    builder.restoreNewlinesState
    templateBodyMarker.done(ScalaElementTypes.TEMPLATE_BODY)
  }
}