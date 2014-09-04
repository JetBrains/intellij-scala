package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Qual_Id
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
 * @author Alexander Podkhalyuzin
 * Date: 05.02.2008
 */

/*
 *  CompilationUnit ::= [package QualId StatementSeparator] TopStatSeq
 */

object CompilationUnit {
  def parse(builder: ScalaPsiBuilder): Int = {
    var parseState = ParserState.EMPTY_STATE

    def parsePackagingBody(hasPackage: Boolean) = {
      while (builder.getTokenType != null) {
        TopStatSeq.parse(builder, false, hasPackage) match {
          case ParserState.EMPTY_STATE =>
          case ParserState.SCRIPT_STATE => parseState = ParserState.SCRIPT_STATE
          case ParserState.FILE_STATE if parseState != ParserState.SCRIPT_STATE => parseState = ParserState.FILE_STATE
          case _ => parseState = ParserState.SCRIPT_STATE
        }
        builder.advanceLexer
      }
    }

    //look for file package
    builder.getTokenType match {
      case ScalaTokenTypes.kPACKAGE => {

        /*
        * Parse sequence of packages according to 2.8 changes
        * */
        def parsePackageSequence(completed: Boolean, k: => Unit) {
          def askType = builder.getTokenType
          if (askType == null) k
          else if (askType == ScalaTokenTypes.tSEMICOLON) {
            builder.advanceLexer
            parsePackageSequence(true, k)
          } else {
            // Mark error
            if (!completed && !builder.newlineBeforeCurrentToken) {
              builder.error(ErrMsg("semi.expected"))
            }
            if (ScalaTokenTypes.kPACKAGE == askType &&
                    !ParserUtils.lookAhead(builder, ScalaTokenTypes.kPACKAGE, ScalaTokenTypes.kOBJECT)) {
              // Parse package statement
              val newMarker = builder.mark
              builder.advanceLexer //package
              askType match {
                case ScalaTokenTypes.tIDENTIFIER => {
                  Qual_Id parse builder
                  // Detect explicit packaging with curly braces
                  if (ParserUtils.lookAhead(builder, ScalaTokenTypes.tLBRACE) &&
                  !builder.getTokenText.matches(".*\n.*\n.*")) {
                    newMarker.rollbackTo
                    parsePackagingBody(true)
                    k
                  } else {
                    parsePackageSequence(false, {newMarker.done(ScalaElementTypes.PACKAGING); k})
                  }
                }
                case _ => {
                  builder error ErrMsg("package.qualID.expected")
                  newMarker.drop
                  parsePackageSequence(true, k)
                }
              }
            } else {
              // Parse the remainder of a file
              parsePackagingBody(true)
              k
            }
          }
        }

        parsePackageSequence(true, ())

      }
      case _ => parsePackagingBody(false)
    }
    return parseState
  }
}