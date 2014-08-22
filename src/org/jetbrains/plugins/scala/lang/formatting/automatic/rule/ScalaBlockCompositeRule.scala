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
                              val indentType: Option[IndentType.IndentType] = None,
                              val priority: Int,
                              val id: String,
                              val relations: Set[(RuleRelation, List[String])] = Set(),
                              val tag: Option[String] = None
                              ) extends ScalaFormattingRule {

  //this is  a transparent wrapper, only the underlying composite rule will be visible
  override def check(block: ScalaBlock,
          parentInfo: Option[RuleParentInfo],
          top: ScalaFormattingRule,
          matcher: ScalaFormattingRuleMatcher,
          missingBlocks: MissingBlocksData*): Option[RuleMatch] = {
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
      if (testFunction(block)) {
        compositeRule.check(block, Some(RuleParentInfo(ruleInstance, 0)), top, matcher, missingBlocks:_*) match {
          case Some(found) =>
            Some(ruleInstance.createMatch(block, found))
          case None => None
        }
      } else None
  }

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher,
                         missingBlocks: MissingBlocksData*): Option[(List[Block], RuleMatch, List[Block])] = {
//    println("checking block composite rule " + id)
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
    val newParentInfo = RuleParentInfo(ruleInstance, 0)
    @tailrec
    def findFirst(before: List[Block], after: List[Block]): Option[(List[Block], RuleMatch, List[Block])] = {
      after match {
        case head :: tail =>
          if (testFunction(head) && head.isInstanceOf[ScalaBlock]) {
            compositeRule.check(head.asInstanceOf[ScalaBlock], Some(newParentInfo), top, matcher, missingBlocks:_*) match {
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

  override def getPresetIndentType: Option[IndentType.IndentType] = indentType

  override def getPriority: Int = priority

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = addTag(new ScalaBlockCompositeRule(testFunction, compositeRule, indentType, priority, id, relations, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = List((compositeRule, 0))

  override protected def addRelation(relation: RuleRelation, args: List[String]): ScalaFormattingRule =
    new ScalaBlockCompositeRule(testFunction, compositeRule, indentType, priority, id, relations + ((relation, args)), tag)

  override def isBlockRule: Boolean = true
}

object ScalaBlockCompositeRule {
  def apply(expectedText: String, compositeRule: ScalaFormattingRule, priority: Int, id: String,
            indentType: Option[IndentType.IndentType]): ScalaFormattingRule = addRule(
    new ScalaBlockCompositeRule(
  {(_: Block) match {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getText == expectedText
    case _ => false
  }}, compositeRule, indentType, priority, id
  ))
  def apply(expectedText: String, compositeRule: ScalaFormattingRule, priority: Int, id: String): ScalaFormattingRule = apply(expectedText, compositeRule, priority, id, None)

  def apply(expectedType: IElementType, compositeRule: ScalaFormattingRule, priority: Int, id: String,
            indentType: Option[IndentType.IndentType]): ScalaFormattingRule = addRule(
    new ScalaBlockCompositeRule(
  {(_: Block) match {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getElementType == expectedType
    case _ => false
  }}, compositeRule, indentType, priority, id
  ))

  def apply(expectedType: IElementType, compositeRule: ScalaFormattingRule, priority: Int, id: String): ScalaFormattingRule =
    apply(expectedType, compositeRule, priority, id, None)

  def apply(compositeRule: ScalaFormattingRule, priority: Int, id: String, expectedTypes: List[IElementType],
            indentType: Option[IndentType.IndentType]): ScalaFormattingRule = addRule(
    new ScalaBlockCompositeRule(
  {(_: Block) match {
    case scalaBlock: ScalaBlock => expectedTypes.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }}, compositeRule, indentType, priority, id
  ))

  def apply(compositeRule: ScalaFormattingRule, priority: Int, id: String, expectedTypes: List[IElementType]): ScalaFormattingRule =
    apply(compositeRule, priority, id, expectedTypes, None)
}