package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Qual_Id
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.annotation.tailrec

/**
  * @author Alexander Podkhalyuzin
  *         Date: 05.02.2008
  */

/*
 *  CompilationUnit ::= [package QualId StatementSeparator] TopStatSeq
 */
object CompilationUnit {

  def parse(builder: ScalaPsiBuilder): Int = {
    var parseState = ParserState.EMPTY_STATE

    def parsePackagingBody(hasPackage: Boolean): Unit = {
      while (builder.getTokenType != null) {
        TopStatSeq.parse(builder, waitBrace = false, hasPackage = hasPackage) match {
          case ParserState.EMPTY_STATE =>
          case ParserState.SCRIPT_STATE =>
            Stats.trigger(FeatureKey.parserScalaScript)
            parseState = ParserState.SCRIPT_STATE
          case ParserState.FILE_STATE if parseState != ParserState.SCRIPT_STATE => parseState = ParserState.FILE_STATE
          case _ => 
            //that means code in the file is probably invalid, so we won't call usage trigger here
            parseState = ParserState.SCRIPT_STATE
        }
        builder.advanceLexer()
      }
    }

    //look for file package
    builder.getTokenType match {
      case ScalaTokenTypes.kPACKAGE =>

        /*
        * Parse sequence of packages according to 2.8 changes
        * */
        @tailrec
        def parsePackageSequence(completed: Boolean, k: => Unit) {
          def askType = builder.getTokenType
          if (askType == null) k
          else if (askType == ScalaTokenTypes.tSEMICOLON) {
            builder.advanceLexer()
            parsePackageSequence(completed = true, k)
          } else {
            // Mark error
            if (!completed && !builder.newlineBeforeCurrentToken) {
              builder.error(ErrMsg("semi.expected"))
            }
            if (ScalaTokenTypes.kPACKAGE == askType &&
              !builder.lookAhead(ScalaTokenTypes.kPACKAGE, ScalaTokenTypes.kOBJECT)) {
              // Parse package statement
              val newMarker = builder.mark
              builder.advanceLexer() //package
              askType match {
                case ScalaTokenTypes.tIDENTIFIER =>
                  Qual_Id parse builder
                  // Detect explicit packaging with curly braces
                  if (builder.lookAhead(ScalaTokenTypes.tLBRACE) &&
                    !builder.getTokenText.matches(".*\n.*\n.*")) {
                    newMarker.rollbackTo()
                    parsePackagingBody(true)
                    k
                  } else {
                    parsePackageSequence(false, {
                      newMarker.done(ScalaElementTypes.PACKAGING)
                      k
                    })
                  }
                case _ =>
                  builder error ErrMsg("package.qualID.expected")
                  newMarker.drop()
                  parsePackageSequence(completed = true, k)
              }
            } else {
              // Parse the remainder of a file
              parsePackagingBody(true)
              k
            }
          }
        }

        parsePackageSequence(completed = true, ())
      case _ => parsePackagingBody(false)
    }
    parseState
  }
}