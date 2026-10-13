package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.lang.LanguageNamesValidation
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.lexer.ScalaIdentifiers
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.SOFT_KEYWORDS

class ScalaNamesValidator extends NamesValidator {

  override def isIdentifier(name: String, project: Project): Boolean =
    ScalaIdentifiers.isIdentifier(name)

  override def isKeyword(name: String, project: Project): Boolean =
    ScalaIdentifiers.isKeyword(name)
}

object ScalaNamesValidator {

  def isKeyword(name: String): Boolean =
    validator.isKeyword(name, null)

  def isIdentifier(name: String): Boolean =
    validator.isIdentifier(name, null)

  private val softKeywords = SOFT_KEYWORDS.getTypes.map(_.toString).toSet

  def isSoftKeyword(name: String): Boolean =
    softKeywords(name)

  // TODO distinguish between Scala 2 & 3
  private def validator: NamesValidator =
    LanguageNamesValidation.INSTANCE.forLanguage(ScalaLanguage.INSTANCE)
}
