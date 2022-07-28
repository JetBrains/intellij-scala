package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

/**
 * [[ClassTemplateBlock]] ::= [EarlyDefs] ClassParents [TemplateBody]
 * | TemplateBody (for 'new' statement)
*/
object ClassTemplateBlock extends ParsingRule{

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val extendsMarker = builder.mark()
    var nonEmpty = false

    builder.getTokenType match {
      //hardly case, because it's same token for ClassParents and TemplateBody
      case ScalaTokenTypes.tLBRACE =>
        nonEmpty = true
        //try to parse early definition if we can't => it's template body
        if (EarlyDef()) {
          NewTemplateDefParents()
          //parse template body
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE if !builder.twoNewlinesBeforeCurrentToken =>
              TemplateBody()
            case InScala3(ScalaTokenTypes.tCOLON) =>
              TemplateBody()
            case _ =>
          }
          extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
          nonEmpty
        }
        else {
          //parse template body
          TemplateBody()
          extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
          nonEmpty
        }
      //if we find nl => it could be TemplateBody only, but we can't find nl after extends keyword
      //In this case of course it's ClassParents
      case _ =>
        if (NewTemplateDefParents()) nonEmpty = true
        else if (true) {
          extendsMarker.drop()
          return false
        }
        //parse template body
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE if !builder.twoNewlinesBeforeCurrentToken =>
            TemplateBody()
          case InScala3(ScalaTokenTypes.tCOLON) =>
            TemplateBody()
          case _ =>
        }
        extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
        nonEmpty
    }
  }
}