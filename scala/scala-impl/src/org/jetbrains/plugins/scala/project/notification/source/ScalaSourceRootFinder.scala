package org.jetbrains.plugins.scala
package project
package notification
package source

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.NullableFunction

import scala.annotation.tailrec

//noinspection TypeAnnotation
final class ScalaSourceRootFinder extends JavaSourceRootDetector {

  import ScalaFileType.{INSTANCE => scalaFileType}

  override def getLanguageName = scalaFileType.getName

  override def getFileExtension = scalaFileType.getDefaultExtension

  override def getPackageNameFetcher: NullableFunction[CharSequence, String] =
    ScalaSourceRootFinder.packageStatement(_: CharSequence)
}

object ScalaSourceRootFinder {

  import StringUtil._
  import lang.lexer.{ScalaLexer, ScalaTokenType, ScalaTokenTypes}
  import ScalaTokenTypes._

  def packageStatement(buf: CharSequence): String = {
    implicit val lexer: ScalaLexer = new ScalaLexer(false, null)
    lexer.start(buf)

    val builder = new StringBuilder()

    readPackage(firstTime = true) { isDot =>
      builder.append(if (isDot) "." else lexer.getTokenText)
    }

    builder.toString match {
      case packageName if isEmpty(packageName) || endsWithChar(packageName, '.') => null
      case packageName => packageName
    }
  }

  @tailrec
  private def readPackage(firstTime: Boolean = false)
                         (append: Boolean => Unit)
                         (implicit lexer: ScalaLexer): Unit = {
    skipWhiteSpaceAndComments(advance = false)

    lexer.getTokenType match {
      case `kPACKAGE` =>
        if (!firstTime) append(true)
        skipWhiteSpaceAndComments()

        lexer.getTokenType match {
          case ScalaTokenType.ObjectKeyword =>
            skipWhiteSpaceAndComments()

            lexer.getTokenType match {
              case `tIDENTIFIER` => append(false)
              case _ =>
            }
          case _ =>
            appendPackageStatement(append)
            readPackage()(append)
        }
      case _ =>
    }
  }

  @tailrec
  private[this] def appendPackageStatement(append: Boolean => Unit)
                                          (implicit lexer: ScalaLexer): Unit =
    lexer.getTokenType match {
      case `tIDENTIFIER` =>
        append(false)
        skipWhiteSpaceAndComments()

        lexer.getTokenType match {
          case `tDOT` =>
            append(true)
            skipWhiteSpaceAndComments()
            appendPackageStatement(append)
          case `tLBRACE` => skipWhiteSpaceAndComments()
          case _ =>
        }
      case `tLBRACE` => skipWhiteSpaceAndComments()
      case _ =>
    }

  private[this] def skipWhiteSpaceAndComments(advance: Boolean = true)
                                             (implicit lexer: ScalaLexer): Unit = {
    if (advance) lexer.advance()
    while (WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(lexer.getTokenType)) {
      lexer.advance()
    }
  }
}