package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc

import org.jetbrains.plugins.scala.ScalaBundle

class ScalaDocWithMonospaceSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = "`"

  override def getTemplateDescription: String = ScalaBundle.message("monospace.surrounder.template.description")
}
