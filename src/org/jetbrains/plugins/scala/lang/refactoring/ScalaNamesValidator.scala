package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.isEmpty
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{KEYWORDS, tIDENTIFIER}

object ScalaNamesValidator extends NamesValidator {

  private val lexerCache = new ThreadLocal[ScalaLexer] {
    override def initialValue(): ScalaLexer = new ScalaLexer
  }

  private val keywordNames: Set[String] = KEYWORDS.getTypes
    .map(_.toString)
    .toSet

  def isIdentifier(name: String, project: Project = null): Boolean = {
    if (isEmpty(name)) return false

    val lexer = lexerCache.get()
    lexer.start(name)

    if (lexer.getTokenType != tIDENTIFIER) return false
    lexer.advance()
    lexer.getTokenType == null
  }

  def isKeyword(name: String, project: Project = null): Boolean =
    keywordNames.contains(name)
}