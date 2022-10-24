package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc

import org.jetbrains.plugins.scala.ScalaBundle

class ScalaDocWithBoldSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getTemplateDescription: String = ScalaBundle.message("bold.surrounder.template.description")

  override def getSyntaxTag = "'''"
}
