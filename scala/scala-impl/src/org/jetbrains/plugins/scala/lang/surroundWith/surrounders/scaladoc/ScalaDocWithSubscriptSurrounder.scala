package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc

import org.jetbrains.plugins.scala.ScalaBundle

class ScalaDocWithSubscriptSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = ",,"

  override def getTemplateDescription: String = ScalaBundle.message("subscript.surrounder.template.description")
}
