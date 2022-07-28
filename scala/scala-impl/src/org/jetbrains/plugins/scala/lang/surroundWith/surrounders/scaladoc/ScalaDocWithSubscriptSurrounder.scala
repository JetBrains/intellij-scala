package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

class ScalaDocWithSubscriptSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = ",,"

  override def getTemplateDescription: String = ScalaBundle.message("subscript.surrounder.template.description")
}
