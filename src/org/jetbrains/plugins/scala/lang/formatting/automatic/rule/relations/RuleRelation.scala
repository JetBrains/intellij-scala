package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule.relations

import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleInstance, ScalaBlockFormatterEntry}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation.RelationParticipantId

/**
 * @author Roman.Shein
 *         Date: 08.05.2014
 */
abstract class RuleRelation {

  private val rules: mutable.ListBuffer[ScalaFormattingRule] = ListBuffer()

  def isAlignedByThisRelation(instance: ScalaFormattingRuleInstance): Boolean = isAlignedByThisRelation(instance.rule)

  def isAlignedByThisRelation(rule: ScalaFormattingRule): Boolean

  /**
   * Given a list of mappings of ruleInstances to entries, construct a new list that contains mappings of ruleInstances
   * to entries such that any choice for any instance inside any mapping is acceptable for this relation.
   * @param rulesToEntries
   */
  def filter(rulesToEntries: List[mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]]): List[mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]]

  def addRule(rule: ScalaFormattingRule, participantIds: RelationParticipantId*) {registerRule(rule)}

  protected def registerRule(rule: ScalaFormattingRule) {rules += rule}

  def getRules: Iterable[ScalaFormattingRule] = rules

  def getAlignedRules: List[ScalaFormattingRule]
}

object RuleRelation {
  type RelationParticipantId = String
}