package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.QualId
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

import scala.annotation.tailrec

/**
 * [[CompilationUnit]] ::= [ 'package' [[QualId]] StatementSeparator ] [[TopStatSeq]]
 */
object CompilationUnit {

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.ObjectKeyword
  import ScalaTokenTypes._

  def apply()(implicit builder: ScalaPsiBuilder): Unit = {
    def parsePackagingBody(): Unit = {
      while (builder.getTokenType != null) {
        TopStatSeq.parse(waitBrace = false)
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
                      case `tLBRACE` | InScala3(ScalaTokenTypes.tCOLON) if !builder.twoNewlinesBeforeCurrentToken =>
                        newMarker.rollbackTo()
                        parsePackagingBody()
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
                parsePackagingBody()
                parsePackage
            }
        }

        parsePackageSequence(completed = true) {}
      case _ =>
        parsePackagingBody()
    }

    while (!builder.eof()) {
      builder.error(ScalaBundle.message("out.of.compilation.unit"))
      builder.advanceLexer()
    }
  }
}