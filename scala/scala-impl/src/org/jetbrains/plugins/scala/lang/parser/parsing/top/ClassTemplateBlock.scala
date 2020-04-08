package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody

/**
 * [[ClassTemplateBlock]] ::= [EarlyDefs] ClassParents [TemplateBody]
 * | TemplateBody (for 'new' statement)
 *
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
* Time: 9:31:16
*/
object ClassTemplateBlock {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val extendsMarker = builder.mark
    var nonEmpty = false

    builder.getTokenType match {
      //hardly case, because it's same token for ClassParents and TemplateBody
      case ScalaTokenTypes.tLBRACE =>
        nonEmpty = true
        //try to parse early definition if we can't => it's template body
        if (EarlyDef parse builder) {
          ClassParents parse builder
          //parse template body
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE if !builder.twoNewlinesBeforeCurrentToken =>
              TemplateBody parse builder
            case ScalaTokenTypes.tCOLON if builder.isScala3 =>
              TemplateBody parse builder
            case _ =>
          }
          extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
          nonEmpty
        }
        else {
          //parse template body
          TemplateBody parse builder
          extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
          nonEmpty
        }
      //if we find nl => it could be TemplateBody only, but we can't find nl after extends keyword
      //In this case of course it's ClassParents
      case _ =>
        if (ClassParents parse builder) nonEmpty = true
        else if (true) {
          extendsMarker.drop()
          return false
        }
        //parse template body
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE if !builder.twoNewlinesBeforeCurrentToken =>
            TemplateBody parse builder
          case ScalaTokenTypes.tCOLON if builder.isScala3 =>
            TemplateBody parse builder
          case _ =>
        }
        extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
        nonEmpty
    }
  }
}