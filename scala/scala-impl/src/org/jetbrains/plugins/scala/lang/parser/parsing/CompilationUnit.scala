package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Qual_Id
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.annotation.tailrec

/**
 * [[CompilationUnit]] ::= [ 'package' [[Qual_Id]] StatementSeparator ] [[TopStatSeq]]
 *
 * @author Alexander Podkhalyuzin
 *         Date: 05.02.2008
 */
object CompilationUnit {

  import ParserState._
  import lexer.ScalaTokenType.ObjectKeyword
  import lexer.ScalaTokenTypes._

  def parse()(implicit builder: ScalaPsiBuilder): Int = {
    var parseState = EMPTY_STATE

    def parsePackagingBody(hasPackage: Boolean): Unit = {
      while (builder.getTokenType != null) {
        TopStatSeq.parse(builder, waitBrace = false, hasPackage) match {
          case EMPTY_STATE =>
          case SCRIPT_STATE =>
            Stats.trigger(FeatureKey.parserScalaScript)
            parseState = SCRIPT_STATE
          case FILE_STATE if parseState != SCRIPT_STATE => parseState = FILE_STATE
          case _ =>
            //that means code in the file is probably invalid, so we won't call usage trigger here
            parseState = SCRIPT_STATE
        }
        builder.advanceLexer()
      }
    }

    //look for file package
    builder.getTokenType match {
      case `kPACKAGE` =>

        /*
        * Parse sequence of packages according to 2.8 changes
        * */
        @tailrec
        def parsePackageSequence(completed: Boolean)
                                (parsePackage: => Unit): Unit = builder.getTokenType match {
          case null => parsePackage
          case `tSEMICOLON` =>
            builder.advanceLexer()
            parsePackageSequence(completed = true)(parsePackage)
          case _ =>
            // Mark error
            if (!completed && !builder.newlineBeforeCurrentToken) {
              builder.error(ScalaBundle.message("semi.expected"))
            }

            builder.getTokenType match {
              case `kPACKAGE` if !builder.lookAhead(kPACKAGE, ObjectKeyword) =>
                // Parse package statement
                val newMarker = builder.mark
                builder.advanceLexer() // Ate package

                builder.getTokenType match {
                  case `tIDENTIFIER` =>
                    Qual_Id.parse(builder)
                    // Detect explicit packaging with curly braces

                    builder.getTokenType match {
                      case `tLBRACE` if !builder.getTokenText.matches(".*\n.*\n.*") =>
                        newMarker.rollbackTo()
                        parsePackagingBody(true)
                        parsePackage
                      case _ =>
                        parsePackageSequence(completed = false) {
                          newMarker.done(ScalaElementType.PACKAGING)
                          parsePackage
                        }
                    }
                  case _ =>
                    builder.error(ScalaBundle.message("package.qualID.expected"))
                    newMarker.drop()
                    parsePackageSequence(completed = true)(parsePackage)
                }
              case _ =>
                // Parse the remainder of a file
                parsePackagingBody(true)
                parsePackage
            }
        }

        parsePackageSequence(completed = true) {
        }
      case _ => parsePackagingBody(false)
    }

    while (!builder.eof()) {
      builder.error(ScalaBundle.message("out.of.compilation.unit"))
      builder.advanceLexer()
    }

    parseState
  }
}