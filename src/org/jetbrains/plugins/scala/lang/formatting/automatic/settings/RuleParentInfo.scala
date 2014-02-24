package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

/**
 * @author Roman.Shein
 *         Date: 24.01.14
 */
class RuleParentInfo(val parent: ScalaFormattingRuleInstance, val position: Int) {
  override def hashCode = parent.hashCode() * position.hashCode()

  override def equals(any: Any) = {
    any match {
      case other: RuleParentInfo => parent == other.parent && position == other.position
      case _ => false
    }
  }

  def getSortTuple = (parent.rule.id, position)
}

object RuleParentInfo {
  def apply(parent: ScalaFormattingRuleInstance, position: Int) = new RuleParentInfo(parent, position)

  def getDefaultSortTuple = ("", 0)
}
