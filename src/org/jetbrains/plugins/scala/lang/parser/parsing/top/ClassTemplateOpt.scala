package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.ClassParents




import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 13:32:17
* To change this template use File | Settings | File Templates.
*/

/*
 * ClassTemplateOpt ::= 'extends' ClassTemplate | [['extends'] TemplateBody]
 */

//May be hard to read. Because written before understanding that before TemplateBody could be nl token
//So there are fixed it, but may be should be some rewrite.
object ClassTemplateOpt {
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
        if (!LineTerminator(builder.getTokenText)) {
          builder.advanceLexer //Ate nl
          val templateMarker = builder.mark
          templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
          extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
          return
        }
        else {
          builder.advanceLexer //Ate nl
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE => {
              TemplateBody parse builder
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
            case _ => {
              val templateMarker = builder.mark
              templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
              extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
              return
            }
          }
        }
      }
      case _ => {
        val templateMarker = builder.mark
        templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
        extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
        return
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
            case ScalaTokenTypes.tLINE_TERMINATOR => {
              if (!LineTerminator(builder.getTokenText)) {
                builder.advanceLexer //Ate nl
                val templateMarker = builder.mark
                templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
                extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                return
              }
              else {
                builder.advanceLexer //Ate nl
                builder.getTokenType match {
                  case ScalaTokenTypes.tLBRACE => {
                    TemplateBody parse builder
                    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                    return
                  }
                  case _ => {
                    val templateMarker = builder.mark
                    templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
                    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                    return
                  }
                }
              }
            }
            case _ => {
              val templateMarker = builder.mark
              templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
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
              val templateMarker = builder.mark
              templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
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
          case ScalaTokenTypes.tLINE_TERMINATOR => {
              if (!LineTerminator(builder.getTokenText)) {
                builder.advanceLexer //Ate nl
                val templateMarker = builder.mark
                templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
                extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                return
              }
              else {
                builder.advanceLexer //Ate nl
                builder.getTokenType match {
                  case ScalaTokenTypes.tLBRACE => {
                    TemplateBody parse builder
                    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                    return
                  }
                  case _ => {
                    val templateMarker = builder.mark
                    templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
                    extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
                    return
                  }
                }
              }
            }
          case _ => {
            val templateMarker = builder.mark
            templateMarker.done(ScalaElementTypes.TEMPLATE_BODY)
            extendsMarker.done(ScalaElementTypes.EXTENDS_BLOCK)
            return
          }
        }
      }
    }
  }
}