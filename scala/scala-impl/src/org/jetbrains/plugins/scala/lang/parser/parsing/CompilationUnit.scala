package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.QualId
import org.jetbrains.plugins.scala.lang.parser.util.InScala3
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.annotation.tailrec

/**
 * [[CompilationUnit]] ::= [ 'package' [[QualId]] StatementSeparator ] [[TopStatSeq]]
 */
object CompilationUnit {

  import ParserState._
  import lexer.ScalaTokenType.ObjectKeyword
  import lexer.ScalaTokenTypes._

  def apply()(implicit builder: ScalaPsiBuilder): ParserState = {
    var parseState: ParserState = EMPTY_STATE

    def parsePackagingBody(hasPackage: Boolean): Unit = {
      while (builder.getTokenType != null) {
        TopStatSeq(waitBrace = false, hasPackage) match {
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
                val newMarker = builder.mark()
                builder.advanceLexer() // Ate package

                builder.getTokenType match {
                  case `tIDENTIFIER` =>
                    QualId()
                    // Detect explicit packaging with curly braces

                    builder.getTokenType match {
                      case `tLBRACE` | InScala3(ScalaTokenTypes.tCOLON) =>
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

        parsePackageSequence(completed = true) {}
      case _ => parsePackagingBody(false)
    }

    while (!builder.eof()) {
      builder.error(ScalaBundle.message("out.of.compilation.unit"))
      builder.advanceLexer()
    }

    parseState
  }
}