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
 * @param composingConditionsIds
 * @param indentType
 * @param priority
 */
class OrRule private (val composingConditionsIds: List[String],
             val indentType: Option[IndentType.IndentType],
             val priority: Int,
             val id: String,
             val structId: String,
             val anchor: Option[Anchor] = None,
             val tag: Option[String] = None,
             val alignmentAnchor: Option[String] = None
             ) extends ScalaFormattingRule{

  def composingConditions = composingConditionsIds.map(getRule)

  private def this(composingConditions: List[ScalaFormattingRule],
  indentType: Option[IndentType.IndentType],
  priority: Int,
  id: String) = this(composingConditions.map(_.id), indentType, priority, id, id)

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
    println("checking or rule " + id)
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
    for ((condition, position) <- childrenWithPosition) {
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

  override def anchor(anchor: Anchor) = {
    assert(this.anchor.isEmpty)
    assert(alignmentAnchor.isEmpty)
    registerRule(new OrRule(composingConditionsIds, indentType, priority, id+"|-"+anchor, structId, Some(anchor), None, Some(anchor)))
  }

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new OrRule(composingConditionsIds, indentType, priority, id + "*" + tag, structId, None, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = composingConditions.zipWithIndex

  override def alignmentAnchor(alignmentAnchor: String): ScalaFormattingRule = {
    assert(anchor.isEmpty)
    assert(alignmentAnchor.isEmpty)
    registerRule(new OrRule(composingConditionsIds, indentType, priority, id + "&" + alignmentAnchor, structId, None, None, Some(alignmentAnchor)))
  }
}

object OrRule {
  def apply(id: String, indentType: IndentType.IndentType, conditions: List[String]) =
    addRule(new OrRule(conditions.toList, Some(indentType), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id, id))
  def apply(id: String, indentType: IndentType.IndentType, conditions: ScalaFormattingRule*) =
    addRule(new OrRule(conditions.toList, Some(indentType), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id))
  def apply(id: String, conditions: ScalaFormattingRule*) =
    addRule(new OrRule(conditions.toList, None, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id))
}
