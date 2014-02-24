package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting.Block
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule.Anchor

/**
 * @author Roman.Shein
 *         Date: 12.09.13
 * Representation of OR regular expression construct for rules.
 * @param composingConditions
 * @param indentType
 * @param priority
 */
class OrRule(val composingConditions: List[ScalaFormattingRule],
             val indentType: Option[IndentType.IndentType],
             priority: Int,
             val id: String,
             val anchor: Option[Anchor] = None) extends ScalaFormattingRule{

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
    println("checking or rule " + id)
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
    for ((condition, position) <- composingConditions.zipWithIndex) {
      condition.checkSome(blocks, Some(RuleParentInfo(ruleInstance, position)), top, matcher) match {
        case Some((before, found, after)) => return Some(before, ruleInstance.createMatch(found), after)
        case None =>
      }
    }
    None
  }

  /**
   * Returns clusters of rules that should be formatted with the same settings.
   * @return
   */
//  override def getSameFormattingRules(instances: List[ScalaFormattingRuleInstance]): List[Set[ScalaFormattingRuleInstance]] = if (sameFormatting)
//    {
//      instances.filter(instance => instance.parentAndPosition match {
//        case Some(parentInfo) => parentInfo.parent == this
//        case _ => false
//      }).groupBy(instance => (instance.root, instance.position)).values.toList.map(_.toSet)
//  } else List[Set[ScalaFormattingRuleInstance]]()

  override def getPresetIndentType: Option[IndentType.IndentType] = indentType

  override def getPriority: Int = priority

  override def anchor(anchor: Anchor) = new OrRule(composingConditions, indentType, priority, id, Some(anchor))
}

object OrRule {
  def apply(id: String, indentType: IndentType.IndentType, conditions: ScalaFormattingRule*) = new OrRule(conditions.toList, Some(indentType), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id)
}
