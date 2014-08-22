package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting._
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import scala.collection.JavaConversions._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType.IndentType
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.FormattingSettings
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation.RelationParticipantId
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{RuleParentInfo, ScalaFormattingRuleMatcher}

/**
 * @author Roman.Shein
 *         Date: 09.09.13
 */
trait ScalaFormattingRule {

  val id: String

  val tag: Option[String]

  val relations: Set[(RuleRelation, List[String])]

  /**
   * Check whether the given sequence of blocks complies with the rule.
   * @param blocks
   * @return
   */
  def check(blocks: List[Block],
            parentAndPosition:Option[RuleParentInfo],
            top: ScalaFormattingRule,
            matcher: ScalaFormattingRuleMatcher,
            missingBlocks: MissingBlocksData*): Option[RuleMatch] =
    checkSome(blocks, parentAndPosition, top, matcher, missingBlocks:_*) match {
      case Some((before, found, after)) /*if before.size == 0 && after.size == 0*/ => Some(found)
      case _ => None
    }

  def check(block: ScalaBlock,
            parentAndPosition:Option[RuleParentInfo],
            top: ScalaFormattingRule,
            matcher: ScalaFormattingRuleMatcher,
            missingBlocks: MissingBlocksData*): Option[RuleMatch] =
    check(block.getSubBlocks().toList, parentAndPosition, top, matcher, missingBlocks:_*)

  /**
   * Check whether some continuous subsequence of the given sequence of blocks complies with the rule.
   * @param blocks
   * @return (before, found, after) where found is a list of blocks that comply with the rule and
   *         before ++ found ++ after == blocks
   */
  def checkSome(blocks: List[Block],
                parentAndPosition: Option[RuleParentInfo],
                top: ScalaFormattingRule,
                matcher: ScalaFormattingRuleMatcher,
                missingBlocks: MissingBlocksData*): Option[(List[Block], RuleMatch, List[Block])]

  def getPresetIndentType: Option[IndentType]

  def getPriority: Int

  override def hashCode = id.hashCode()

  override def equals(other: Any) = {
    other match {
      case rule: ScalaFormattingRule => rule.id == id && rule.relations == relations
    }
  }

  protected def addRelation(relation: RuleRelation, args: List[String]): ScalaFormattingRule

  def tag(tag: String): ScalaFormattingRule

  /**
   * Creates a new rule with same parameters as this, but also dependent on relation given.
   * @param relation
   * @return
   */
  def acceptRelation(relation: RuleRelation, participantIds: RelationParticipantId*): ScalaFormattingRule = {
    val newRule = addRelation(relation, participantIds.toList)
    relation.addRule(newRule, participantIds:_*)
    addRule(newRule)
    newRule
  }

  override def toString = id

  def childrenWithPosition: List[(ScalaFormattingRule, Int)]

  /**
   * Zero or more of.
   * @return
   */
  def * = ScalaSomeRule(0, this, zeroOrMoreId + id)

  /**
   * One or more of
   * @return
   */
  def + = ScalaSomeRule(1, this, oneOrMoreId + id)

  def &(relationId: String, additionalIds: String*) = rule.&(this, relationId, additionalIds:_*)

  def n = rule.n(this)

  def c = rule.c(this)

  def isBlockRule: Boolean = false
}

object ScalaFormattingRule {

  type Anchor = String

  val RULE_PRIORITY_DEFAULT = 0
}