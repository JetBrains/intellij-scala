package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

/**
 * User: Dmitry Naydanov
 * Date: 3/2/12
 */

class ScalaDocWithBoldSurrounder extends ScalaDocWithSyntaxSurrounder {
  def getTemplateDescription: String = "Bold: ''' '''"

  def getSyntaxTag = "'''"
}
