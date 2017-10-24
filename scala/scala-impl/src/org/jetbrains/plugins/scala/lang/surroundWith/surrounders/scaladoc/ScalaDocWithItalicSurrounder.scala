package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

/**
 * User: Dmitry Naydanov
 * Date: 3/8/12
 */

class ScalaDocWithItalicSurrounder extends ScalaDocWithSyntaxSurrounder {
  def getSyntaxTag: String = "''"

  def getTemplateDescription: String = "Italic: '' ''"
}
