package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

/**
 * User: Dmitry Naydanov
 * Date: 3/8/12
 */

class ScalaDocWithSubscriptSurrounder extends ScalaDocWithSyntaxSurrounder {
  override def getSyntaxTag: String = ",,"

  override def getTemplateDescription: String = ScalaBundle.message("subscript.surrounder.template.description")
}
