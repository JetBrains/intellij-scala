package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting._
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{RuleParentInfo, ScalaFormattingRuleMatcher}

/**
 * @author Roman.Shein
 *         Date: 09.09.13
 */
class ScalaBlockRule private (val testFunction: Block => Boolean,
                     val indentType: Option[IndentType.IndentType],
                     val id: String,
                     val relations: Set[(RuleRelation, List[String])] = Set(),
                     val tag: Option[String] = None
                    ) extends ScalaFormattingRule {

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher,
                         missingBlocks: MissingBlocksData*) = {
    val parentBlock = blocks.headOption.map(_.asInstanceOf[ScalaBlock].myParentBlock)
//    println("checking block rule " + id)
    val (before, foundBlock, after, isMatched) =
    blocks.zipWithIndex.foldLeft(List[Block](), None: Option[Block], List[Block](), false)((acc, blockAndIndex) => {
      val (block, index) = blockAndIndex
      val (before, foundBlock, after, isMatched) = acc
      if (parentBlock.isDefined && missingBlocks.contains(MissingBlocksData(parentBlock.get, index)))
        (before, None, after, true)
      else if (isMatched) (before, foundBlock, block::after, isMatched)
      else if (testFunction(block)) (before, Some(block), after, true)
      else (block::before, foundBlock, after, isMatched)
    })
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
    if (isMatched) Some(before.reverse,
      if (foundBlock.isDefined && foundBlock.get.isInstanceOf[ScalaBlock])
        ruleInstance.createMatch(foundBlock.get.asInstanceOf[ScalaBlock])
      else ruleInstance.createMatch(),
    after.reverse) else None
  }

  override def getPresetIndentType: Option[IndentType.IndentType] = indentType

  override def getPriority: Int = ScalaFormattingRule.RULE_PRIORITY_DEFAULT

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = addTag(new ScalaBlockRule(testFunction, indentType, id, relations, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = List()

  override protected def addRelation(relation: RuleRelation, args: List[String]): ScalaFormattingRule =
    new ScalaBlockRule(testFunction, indentType, id, relations + ((relation, args)), tag)

  override def isBlockRule: Boolean = true
}

object ScalaBlockRule {
  def apply(testFunction: Block => Boolean, id: String, indentType: Option[IndentType.IndentType]): ScalaFormattingRule =
    addRule(new ScalaBlockRule(testFunction, None, id))
  def apply(testFunction: Block => Boolean, id: String): ScalaFormattingRule = apply(testFunction, id, None)
  def apply(expectedText: String, id:String, indentType: Option[IndentType.IndentType]): ScalaFormattingRule =
    addRule(new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getText == expectedText
    case _ => false
  }, None, id
  ))

  def apply(expectedText: String, id:String): ScalaFormattingRule = apply(expectedText, id, None)
  def apply(id:String, indentType: Option[IndentType.IndentType], expectedType: IElementType*): ScalaFormattingRule = addRule(new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => expectedType.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }, indentType, id
  ))
  def apply(id: String, expectedType: IElementType*): ScalaFormattingRule = apply(id, None, expectedType:_*)
  def apply(id:String, expectedType: List[IElementType], indentType: Option[IndentType.IndentType]): ScalaFormattingRule =
  addRule(new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => expectedType.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }, None, id
  ))
  def apply(id:String, expectedType: List[IElementType]): ScalaFormattingRule = apply(id, expectedType, None)
  def apply(expectedType: IElementType, expectedText: String, id: String,
            indentType: Option[IndentType.IndentType] = None): ScalaFormattingRule = addRule(new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getElementType == expectedType && scalaBlock.getNode.getText == expectedText
    case _ => false
  }, None, id
  ))
  def apply(expectedType: IElementType, expectedText: String, id: String): ScalaFormattingRule =
    apply(expectedType, expectedText, id, None)
}


