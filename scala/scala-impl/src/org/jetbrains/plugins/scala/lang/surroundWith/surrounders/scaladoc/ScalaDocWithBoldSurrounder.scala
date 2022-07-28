package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

class ScalaDocWithBoldSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getTemplateDescription: String = ScalaBundle.message("bold.surrounder.template.description")

  override def getSyntaxTag = "'''"
}
