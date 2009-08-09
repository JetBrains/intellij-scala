package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator
import template.TemplateBody

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * TraitTemplateOpt ::= 'extends' TraitTemplate | [['extends'] TemplateBody]
 */

//It's very similar code to ClassTemplateOpt
object TraitTemplateOpt {
  def parse(builder: PsiBuilder): Unit = {
    val extendsMarker = builder.mark
    //try to find extends keyword
    builder.getTokenType match {
      case ScalaTokenTypes.kEXTENDS | ScalaTokenTypes.tUPPER_BOUND=> builder.advanceLexer //Ate extends
      case ScalaTokenTypes.tLBRACE => {
        TemplateBody parse builder
        extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
        return
      }
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        val rollbackMarker = builder.mark
        if (!LineTerminator(builder.getTokenText)) {
          //builder.advanceLexer //Ate nl
          rollbackMarker.drop
          extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
          return
        }
        else {
          builder.advanceLexer //Ate nl
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE => {
              rollbackMarker.drop
              TemplateBody parse builder
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
            case _ => {
              rollbackMarker.rollbackTo
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
          }
        }
      }
      case _ => {
        extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
        return
      }
    }
    // try to split TraitParents and TemplateBody
    builder.getTokenType match {
      //hardly case, becase it's same token for ClassParents and TemplateBody
      case ScalaTokenTypes.tLBRACE => {
        //try to parse early definition if we can't => it's template body
        if (EarlyDef parse builder) {
          MixinParents parse builder
          //parse template body
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE => {
              TemplateBody parse builder
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
            case ScalaTokenTypes.tLINE_TERMINATOR => {
              val rollbackMarker = builder.mark
              if (!LineTerminator(builder.getTokenText)) {
                //builder.advanceLexer //Ate nl
                rollbackMarker.drop
                extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                return
              }
              else {
                builder.advanceLexer //Ate nl
                builder.getTokenType match {
                  case ScalaTokenTypes.tLBRACE => {
                    rollbackMarker.drop
                    TemplateBody parse builder
                    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                    return
                  }
                  case _ => {
                    rollbackMarker.rollbackTo
                    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                    return
                  }
                }
              }
            }
            case _ => {
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
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
        MixinParents parse builder
        //parse template body
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            TemplateBody parse builder
            extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
            return
          }
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            val rollbackMarker = builder.mark
            if (!LineTerminator(builder.getTokenText)) {
              //builder.advanceLexer //Ate nl
              rollbackMarker.drop
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
            else {
              builder.advanceLexer //Ate nl
              builder.getTokenType match {
                case ScalaTokenTypes.tLBRACE => {
                  rollbackMarker.drop
                  TemplateBody parse builder
                  extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                  return
                }
                case _ => {
                  rollbackMarker.rollbackTo
                  extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                  return
                }
              }
            }
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