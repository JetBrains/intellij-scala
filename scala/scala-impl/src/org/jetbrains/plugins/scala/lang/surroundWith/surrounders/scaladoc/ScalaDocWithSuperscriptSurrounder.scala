package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

/**
 * User: Dmitry Naydanov
 * Date: 3/8/12
 */

class ScalaDocWithSuperscriptSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = "^"

  override def getTemplateDescription: String = ScalaBundle.message("superscript.surrounder.template.description")
}
