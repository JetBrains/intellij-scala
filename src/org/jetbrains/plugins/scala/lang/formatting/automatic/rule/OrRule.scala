package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting.Block
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule.Anchor
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation

/**
 * @author Roman.Shein
 *         Date: 12.09.13
 * Representation of OR regular expression construct for rules.
 * @param composingConditions
 * @param indentType
 * @param priority
 */
class OrRule private (val composingConditions: List[ScalaFormattingRule],
             val indentType: Option[IndentType.IndentType],
             val priority: Int,
             val id: String,
             val relations: Set[(RuleRelation, List[String])] = Set(),
             val tag: Option[String] = None
             ) extends ScalaFormattingRule{

//  def composingConditions = composingConditionsIds.map(getRule)

//  private def this(composingConditions: List[ScalaFormattingRule],
//  indentType: Option[IndentType.IndentType],
//  priority: Int,
//  id: String) = this(composingConditions, indentType, priority, id)

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

  override def getPresetIndentType: Option[IndentType.IndentType] = indentType

  override def getPriority: Int = priority

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new OrRule(composingConditions, indentType, priority, id, relations, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = composingConditions.zipWithIndex

  override protected def addRelation(relation: RuleRelation, args: List[String]): ScalaFormattingRule =
    new OrRule(composingConditions, indentType, priority, id, relations + ((relation, args)), tag)
}

object OrRule {
//  def apply(id: String, indentType: IndentType.IndentType, conditions: List[String]) =
//    addRule(new OrRule(conditions.toList, Some(indentType), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id))
  def apply(id: String, indentType: IndentType.IndentType, conditions: ScalaFormattingRule*) =
    addRule(new OrRule(conditions.toList, Some(indentType), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id))
  def apply(id: String, conditions: ScalaFormattingRule*) =
    addRule(new OrRule(conditions.toList, None, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id))
  def apply(id: String, indentType: Option[IndentType.IndentType], conditions: ScalaFormattingRule*) =
    addRule(new OrRule(conditions.toList, indentType, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id))
}
