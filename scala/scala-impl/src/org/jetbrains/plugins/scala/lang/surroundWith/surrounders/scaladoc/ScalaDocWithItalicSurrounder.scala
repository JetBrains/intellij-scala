package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

class ScalaDocWithItalicSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = "''"

  override def getTemplateDescription: String = ScalaBundle.message("italic.surrounder.template.description")
}
