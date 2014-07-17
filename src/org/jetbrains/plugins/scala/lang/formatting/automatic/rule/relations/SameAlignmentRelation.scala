package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule.relations

import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation._

class SameAlignmentRelation(override val id: String) extends SameSettingsRelation(id) {
  override def addRule(rule: ScalaFormattingRule, participantIds: RelationParticipantId*) {
    registerRule(rule)
    sameAlignmentRules = rule :: sameAlignmentRules
  }
}
