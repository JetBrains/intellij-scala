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
* Date: 06.02.2008
*/

/*
 * ClassTemplateOpt ::= 'extends' ClassTemplate | [['extends'] TemplateBody]
 */

//May be hard to read. Because written before understanding that before TemplateBody could be nl token
//So there are fixed it, but may be should be some rewrite.
object ClassTemplateOpt {
  def parse(builder: ScalaPsiBuilder): Unit = {
    val extendsMarker = builder.mark
    //try to find extends keyword
    builder.getTokenType match {
      case ScalaTokenTypes.kEXTENDS | ScalaTokenTypes.tUPPER_BOUND => builder.advanceLexer //Ate extends
      case ScalaTokenTypes.tLBRACE => {
        TemplateBody parse builder
        extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
        return
      }
      case _ => {
        if (builder.twoNewlinesBeforeCurrentToken) {
          extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
          return
        }
        else {
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE => {
              TemplateBody parse builder
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
            case _ => {
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
          }
        }
      }
    }
    // try to split ClassParents and TemplateBody
    builder.getTokenType match {
      //hardly case, becase it's same token for ClassParents and TemplateBody
      case ScalaTokenTypes.tLBRACE => {
        //try to parse early definition if we can't => it's template body
        if (EarlyDef parse builder) {
          ClassParents parse builder
          //parse template body
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE => {
              TemplateBody parse builder
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
            case _ => {
              if (builder.twoNewlinesBeforeCurrentToken) {
                extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                return
              }
              else {
                builder.getTokenType match {
                  case ScalaTokenTypes.tLBRACE => {
                    TemplateBody parse builder
                    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                    return
                  }
                  case _ => {
                    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                    return
                  }
                }
              }
            }
          }
        }
        else {
          //parse template body
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE => {
              TemplateBody parse builder
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
            case _ => {
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
          }
        }
      }
      //if we find nl => it could be TemplateBody only, but we can't find nl after extends keyword
      //In this case of course it's ClassParents
      case _ => {
        ClassParents parse builder
        //parse template body
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            TemplateBody parse builder
            extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
            return
          }
          case _ =>
            if (builder.twoNewlinesBeforeCurrentToken) {
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
            else {
              builder.getTokenType match {
                case ScalaTokenTypes.tLBRACE => {
                  TemplateBody parse builder
                  extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                  return
                }
                case _ => {
                  extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                  return
                }
              }
            }
        }
      }
    }
  }
}