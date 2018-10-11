package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.{MixinParents, TemplateBody}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.02.2008
  */

/*
 * TraitTemplateOpt ::= 'extends' TraitTemplate | [['extends'] TemplateBody]
 */
//It's very similar code to ClassTemplateOpt
object TraitTemplateOpt {

  def parse(implicit builder: ScalaPsiBuilder): Unit = {
    val extendsMarker = builder.mark
    //try to find extends keyword
    builder.getTokenType match {
      case ScalaTokenTypes.kEXTENDS | ScalaTokenTypes.tUPPER_BOUND =>
        builder.advanceLexer() //Ate extends

        // try to split TraitParents and TemplateBody
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE if !EarlyDef.parse(builder) =>
            TemplateBody.parse(builder)
          case _ =>
            MixinParents.parse(builder)
            TemplateOpt.parseTemplateBody
        }
      case ScalaTokenTypes.tLBRACE if !builder.twoNewlinesBeforeCurrentToken => TemplateBody.parse(builder)
      case _ =>
    }
    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
  }
}