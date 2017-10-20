package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

/**
 * User: Dmitry Naydanov
 * Date: 3/5/12
 */

class ScalaDocWithUnderlinedSurrounder extends ScalaDocWithSyntaxSurrounder {
  def getSyntaxTag = "__"

  def getTemplateDescription: String = "Underline: __ __"
}
