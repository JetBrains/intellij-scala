package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.openapi.util.Condition
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorConditions.ExpandedCondition
import scala.language.implicitConversions

/**
 * @author Roman.Shein
 * @since 11.09.2015.
 */
package object templates {
  implicit def toExpandedCondition[T](condition: Condition[T]): ExpandedCondition[T] = new ExpandedCondition(condition)
}
