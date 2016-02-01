package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.{ClassParents, TemplateBody}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
* Time: 9:31:16
*/

/*
 * ClassTemplate ::= [EarlyDefs] ClassParents [TemplateBody]
 *                 | TemplateBody (for 'new' statement)
 */
object ClassTemplate extends ClassTemplate {
  override protected val earlyDef = EarlyDef
  override protected val classParents = ClassParents
  override protected val templateBody = TemplateBody
}

trait ClassTemplate {
  protected val earlyDef: EarlyDef
  protected val templateBody: TemplateBody
  protected val classParents: ClassParents

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder, nonEmpty = false)
  def parse(builder: ScalaPsiBuilder, nonEmpty: Boolean): Boolean = {
    val extendsMarker = builder.mark
    var empty = true
    builder.getTokenType match {
      //hardly case, becase it's same token for ClassParents and TemplateBody
      case ScalaTokenTypes.tLBRACE =>
        empty = false
        //try to parse early definition if we can't => it's template body
        if (earlyDef parse builder) {
          classParents parse builder
          //parse template body
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE => {
              if (builder.twoNewlinesBeforeCurrentToken) {
                extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                return !nonEmpty || !empty
              }
              templateBody parse builder
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              !nonEmpty || !empty
            }
            case _ => {
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              !nonEmpty || !empty
            }
          }
        }
        else {
          //parse template body
          templateBody parse builder
          extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
          !nonEmpty || !empty
        }
      //if we find nl => it could be TemplateBody only, but we can't find nl after extends keyword
      //In this case of course it's ClassParents
      case _ =>
        if (classParents parse builder) empty = false
        else if (nonEmpty) {
          extendsMarker.drop()
          return false
        }
        //parse template body
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            if (builder.twoNewlinesBeforeCurrentToken) {
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return !nonEmpty || !empty
            }
            templateBody parse builder
            extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
            !nonEmpty || !empty
          }
          case _ => {
            extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
            !nonEmpty || !empty
          }
        }
    }
  }
}