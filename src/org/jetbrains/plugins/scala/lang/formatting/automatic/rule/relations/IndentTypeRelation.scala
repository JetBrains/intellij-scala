package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule.relations

import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation._
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{ScalaFormattingRuleInstance, ScalaBlockFormatterEntry}
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.tree.FormattingSettingsTree
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType

/**
 * Created by Roman.Shein on 14.08.2014.
 */
class IndentTypeRelation(override val id: String, val indentTypeValue: IndentType.IndentType) extends SameSettingsRelation(id) {

  override def indentType = Some(indentTypeValue)

  override val isIndentTypeRelation = true

  override def addRule(rule: ScalaFormattingRule, participantIds: RelationParticipantId*) {
    registerRule(rule)
    sameIndentRules = rule :: sameIndentRules
  }
}

object IndentTypeRelation {
  val normalIndentRelation = new IndentTypeRelation("NORMAL INDENT RELATION", IndentType.NormalIndent)
  val continuationIndentRelation = new IndentTypeRelation("CONTINUATION INDENT RELATION", IndentType.ContinuationIndent)
}
