package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.StatementSeparator
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AttributeClause
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.ScalaBundle
//import org.jetbrains.plugins.scala.ScalaBundleImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Qual_Id

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 05.02.2008
* Time: 13:54:10
* To change this template use File | Settings | File Templates.
*/

/*
 *  CompilationUnit ::= [package QualId StatementSeparator] TopStatSeq
 */

object CompilationUnit {
  def parse(builder: PsiBuilder) = {
    //look for file package
    builder.getTokenType match {
      case ScalaTokenTypes.kPACKAGE => {
        val packChooseMarker = builder.mark()
        builder.advanceLexer //Ate package

        //Look for identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => Qual_Id parse builder
          case _ => builder error ScalaBundle.message("package.qualID.expected", new Array[Object](0))
          }

        //try to split package and packaging
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON => {
            builder.advanceLexer //Ate semicolon
            packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
          }

          case ScalaTokenTypes.tLBRACE =>  {
            builder.advanceLexer //Ate '{'
            //parse packaging body
            TopStatSeq parse builder
            //Look for '}'
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => builder.advanceLexer //Ate '}'
              case _ => builder error ScalaBundle.message("rbrace.expected", new Array[Object](0))
            }
            packChooseMarker.done(ScalaElementTypes.PACKAGING)
          }

          case ScalaTokenTypes.tLINE_TERMINATOR => {
            //Single or multiple new-line token
            if (LineTerminator(builder.getTokenText)) {
              builder.advanceLexer //Ate new-line token
              //if { => packaging
              builder.getTokenType match {
                case ScalaTokenTypes.tLBRACE =>  {
                  builder.advanceLexer //Ate '{'
                  //parse packaging body
                  TopStatSeq parse builder
                  //Look for '}'
                  builder.getTokenType match {
                    case ScalaTokenTypes.tRBRACE => builder.advanceLexer //Ate '}'
                    case _ => builder error ScalaBundle.message("rbrace.expected", new Array[Object](0))
                  }
                  packChooseMarker.done(ScalaElementTypes.PACKAGING)
                }
                case _ => {
                  packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
                }
              }
            }
            else {
              builder.advanceLexer //Ate new-line token
              packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
            }
          }

          //if builder.eof
          case null => {
            packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
          }

          case _ => {
            builder error ScalaBundle.message("semi.expected", new Array[Object](0))
            packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
          }
        }
        TopStatSeq parse builder
      }
      case _ => TopStatSeq parse builder
    }
  }
}