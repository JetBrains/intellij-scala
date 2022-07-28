package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

class ScalaDocWithMonospaceSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = "`"

  override def getTemplateDescription: String = ScalaBundle.message("monospace.surrounder.template.description")
}
