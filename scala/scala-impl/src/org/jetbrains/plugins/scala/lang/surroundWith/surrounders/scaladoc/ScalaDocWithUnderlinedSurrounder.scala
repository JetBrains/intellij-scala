package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

class ScalaDocWithUnderlinedSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag = "__"

  override def getTemplateDescription: String = ScalaBundle.message("underline.surrounder.template.description")
}
