package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

/**
 * User: Dmitry Naydanov
 * Date: 3/2/12
 */

class ScalaDocWithBoldSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getTemplateDescription: String = ScalaBundle.message("bold.surrounder.template.description")

  override def getSyntaxTag = "'''"
}
