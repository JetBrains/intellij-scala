package org.jetbrains.plugins.scala
package project
package notification
package source

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.NullableFunction

import scala.annotation.tailrec

/**
 * @author Alexander Podkhalyuzin
 */
//noinspection TypeAnnotation
final class ScalaSourceRootFinder extends JavaSourceRootDetector {

  import ScalaFileType.{INSTANCE => scalaFileType}

  def getLanguageName = scalaFileType.getName

  def getFileExtension = scalaFileType.getDefaultExtension

  def getPackageNameFetcher: NullableFunction[CharSequence, String] =
    ScalaSourceRootFinder.packageStatement(_: CharSequence)
}

object ScalaSourceRootFinder {

  import StringUtil._
  import lang.lexer.{ScalaLexer, ScalaTokenTypes}
  import ScalaTokenTypes.{WHITES_SPACES_AND_COMMENTS_TOKEN_SET, kOBJECT => Object, kPACKAGE => Package, tDOT => Dot, tIDENTIFIER => Identifier, tLBRACE => LeftBrace}

  def packageStatement(buf: CharSequence): String = {
    implicit val lexer: ScalaLexer = new ScalaLexer
    lexer.start(buf)

    val builder = StringBuilder.newBuilder

    readPackage(firstTime = true) { isDot =>
      builder.append(if (isDot) "." else lexer.getTokenText)
    }

    builder.toString match {
      case packageName if isEmpty(packageName) || endsWithChar(packageName, '.') => null
      case packageName => packageName
    }
  }

  private def readPackage(firstTime: Boolean = false)
                         (append: Boolean => Unit)
                         (implicit lexer: ScalaLexer): Unit = {
    skipWhiteSpaceAndComments(advance = false)

    lexer.getTokenType match {
      case Package =>
        if (!firstTime) append(true)
        skipWhiteSpaceAndComments()

        lexer.getTokenType match {
          case Object =>
            skipWhiteSpaceAndComments()

            lexer.getTokenType match {
              case Identifier => append(false)
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
      case Identifier =>
        append(false)
        skipWhiteSpaceAndComments()

        lexer.getTokenType match {
          case Dot =>
            append(true)
            skipWhiteSpaceAndComments()
            appendPackageStatement(append)
          case LeftBrace => skipWhiteSpaceAndComments()
          case _ =>
        }
      case LeftBrace => skipWhiteSpaceAndComments()
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