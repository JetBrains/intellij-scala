package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
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
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Qual_Id

/** 
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
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

        def parsePackageStmt = {
          //try to split package and packaging
          builder.getTokenType match {
            case ScalaTokenTypes.tSEMICOLON => {
              packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
            }

            case ScalaTokenTypes.tLBRACE => {
              builder.advanceLexer //Ate '{'
              //parse packaging body
              TopStatSeq parse (builder, true)
              //Look for '}'
              builder.getTokenType match {
                case ScalaTokenTypes.tRBRACE => builder.advanceLexer
                //Ate '}'
                case _ => builder error ScalaBundle.message("rbrace.expected")
              }
              packChooseMarker.done(ScalaElementTypes.PACKAGING)
            }

            case ScalaTokenTypes.tLINE_TERMINATOR => {
              //Single or multiple new-line token
              if (LineTerminator(builder.getTokenText)) {
                builder.advanceLexer //Ate new-line token
                //if { => packaging
                builder.getTokenType match {
                  case ScalaTokenTypes.tLBRACE => {
                    builder.advanceLexer //Ate '{'
                    //parse packaging body
                    TopStatSeq parse (builder, true)
                    //Look for '}'
                    builder.getTokenType match {
                      case ScalaTokenTypes.tRBRACE => builder.advanceLexer
                      //Ate '}'
                      case _ => builder error ScalaBundle.message("rbrace.expected")
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
              builder error ScalaBundle.message("semi.expected")
              packChooseMarker.done(ScalaElementTypes.PACKAGE_STMT)
            }
          }
        }

        //Look for identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            Qual_Id parse builder
            parsePackageStmt
          }
          case _ => {
            builder error ErrMsg("package.qualID.expected")
            packChooseMarker.drop
          }
        }



        while (builder.getTokenType != null) {
          TopStatSeq parse builder
          builder.advanceLexer
        }
      }

      case _ => {
        while (builder.getTokenType != null) {
          TopStatSeq parse builder
          builder.advanceLexer
        }
      }
    }
  }
}