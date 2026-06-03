package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc

import org.jetbrains.plugins.scala.ScalaBundle

class ScalaDocWithSuperscriptSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = "^"

  override def getTemplateDescription: String = ScalaBundle.message("superscript.surrounder.template.description")
}
