package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

class ScalaDocWithSuperscriptSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = "^"

  override def getTemplateDescription: String = ScalaBundle.message("superscript.surrounder.template.description")
}
