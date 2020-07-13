package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

/**
 * User: Dmitry Naydanov
 * Date: 3/8/12
 */

class ScalaDocWithMonospaceSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = "`"

  override def getTemplateDescription: String = ScalaBundle.message("monospace.surrounder.template.description")
}
