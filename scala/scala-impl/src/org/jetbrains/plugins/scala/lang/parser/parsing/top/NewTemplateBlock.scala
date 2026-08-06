package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

/**
 * [[NewTemplateBlock]] ::= [EarlyDefs] ClassParents [TemplateBody]
 * | TemplateBody
*/
object NewTemplateBlock extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): true = {
    val extendsMarker = builder.mark()

    builder.getTokenType match {
      //hardly case, because it's same token for ClassParents and TemplateBody
      case ScalaTokenTypes.tLBRACE =>
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
        }
        else {
          //parse template body
          TemplateBody()
          extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
        }
      case InScala3(ScalaTokenTypes.tCOLON) =>
        TemplateBody()
        extendsMarker.done(ScalaElementType.EXTENDS_BLOCK)
      //if we find nl => it could be TemplateBody only, but we can't find nl after extends keyword
      //In this case of course it's ClassParents
      case _ =>
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
    }
    true
  }
}