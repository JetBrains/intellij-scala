package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.02.2008
  */

/*
 * ClassTemplateOpt ::= 'extends' ClassTemplate | [['extends'] TemplateBody]
 */
//May be hard to read. Because written before understanding that before TemplateBody could be nl token
//So there are fixed it, but may be should be some rewrite.
object ClassTemplateOpt {

  def parse(implicit builder: ScalaPsiBuilder): Unit = {
    val extendsMarker = builder.mark
    //try to find extends keyword
    builder.getTokenType match {
      case ScalaTokenTypes.kEXTENDS | ScalaTokenTypes.tUPPER_BOUND =>
        builder.advanceLexer() //Ate extends

        // try to split ClassParents and TemplateBody
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE if !EarlyDef.parse(builder) =>
            TemplateBody.parse(builder)
          case _ =>
            ClassParents.parse
            TemplateOpt.parseTemplateBody
        }
      case ScalaTokenTypes.tLBRACE => TemplateBody.parse(builder)
      case _ =>
    }

    extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
  }
}