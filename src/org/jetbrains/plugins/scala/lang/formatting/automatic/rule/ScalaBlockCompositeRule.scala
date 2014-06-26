package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType
import com.intellij.formatting.Block
import scala.collection.JavaConversions._
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.psi.tree.IElementType
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{RuleParentInfo, ScalaFormattingRuleMatcher}

/**
 * @author Roman.Shein
 *         Date: 27.11.13
 */
class ScalaBlockCompositeRule private (val testFunction: Block => Boolean,
                              val compositeRule: ScalaFormattingRule,
                              val priority: Int,
                              val id: String,
                              val relations: Set[(RuleRelation, List[String])] = Set(),
                              val tag: Option[String] = None
                              ) extends ScalaFormattingRule {
//  def compositeRule = getRule(compositeRuleId)

//  private def this(testFunction: Block => Boolean,
//  compositeRule: ScalaFormattingRule,
//  priority: Int,
//  id: String) = this(testFunction, compositeRule, priority, id)

  //this is  a transparent wrapper, only the underlying composite rule will be visible
  override def check(block: ScalaBlock,
          parentInfo: Option[RuleParentInfo],
          top: ScalaFormattingRule,
          matcher: ScalaFormattingRuleMatcher): Option[RuleMatch] = {
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
      if (testFunction(block)) {
        compositeRule.check(block, Some(RuleParentInfo(ruleInstance, 0)), top, matcher) match {
          case Some(found) =>
            Some(ruleInstance.createMatch(block, found))
          case None => None
        }
      } else None
  }

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
//    println("checking block composite rule " + id)
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
    val newParentInfo = RuleParentInfo(ruleInstance, 0)
    @tailrec
    def findFirst(before: List[Block], after: List[Block]): Option[(List[Block], RuleMatch, List[Block])] = {
      after match {
        case head :: tail =>
          if (testFunction(head) && head.isInstanceOf[ScalaBlock]) {
            compositeRule.check(head.asInstanceOf[ScalaBlock], Some(newParentInfo), top, matcher) match {
              case Some(ruleMatch) => Some((before.reverse, ruleInstance.createMatch(head.asInstanceOf[ScalaBlock], ruleMatch), tail))
              case None => findFirst(head :: before, tail)
            }
          } else {
            findFirst(head :: before, tail)
          }
        case _ => None
      }
    }
    findFirst(List[Block](), blocks)
  }

  override def getPresetIndentType: Option[IndentType.IndentType] = compositeRule.getPresetIndentType

  override def getPriority: Int = priority

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new ScalaBlockCompositeRule(testFunction, compositeRule, priority, id, relations, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = List((compositeRule, 0))

  override protected def addRelation(relation: RuleRelation, args: List[String]): ScalaFormattingRule =
    new ScalaBlockCompositeRule(testFunction, compositeRule, priority, id, relations + ((relation, args)), tag)

  override def isBlockRule: Boolean = true
}

object ScalaBlockCompositeRule {
  def apply(expectedText: String, compositeRule: ScalaFormattingRule, priority: Int, id: String) = addRule(
    new ScalaBlockCompositeRule(
  {(_: Block) match {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getText == expectedText
    case _ => false
  }}, compositeRule, priority, id
  ))

  def apply(expectedType: IElementType, compositeRule: ScalaFormattingRule, priority: Int, id: String) = addRule(
    new ScalaBlockCompositeRule(
  {(_: Block) match {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getElementType == expectedType
    case _ => false
  }}, compositeRule, priority, id
  ))

  def apply(compositeRule: ScalaFormattingRule, priority: Int, id: String, expectedTypes: List[IElementType]) = addRule(
    new ScalaBlockCompositeRule(
  {(_: Block) match {
    case scalaBlock: ScalaBlock => expectedTypes.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }}, compositeRule, priority, id
  ))
}