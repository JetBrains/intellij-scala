package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc

import org.jetbrains.plugins.scala.ScalaBundle

class ScalaDocWithUnderlinedSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag = "__"

  override def getTemplateDescription: String = ScalaBundle.message("underline.surrounder.template.description")
}
