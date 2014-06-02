package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting.Block
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation

/**
 * @author Roman.Shein
 *         Date: 12.09.13
 */
class MaybeRule private (val inner: ScalaFormattingRule,
                val indentType: Option[IndentType.IndentType],
                val priority: Int,
                val id: String,
                val relations: Set[(RuleRelation, List[String])] = Set(),
                val tag: Option[String] = None) extends ScalaFormattingRule{

//  private def this(innerRule: ScalaFormattingRule,
//      indentType: Option[IndentType.IndentType],
//      priority: Int,
//      id: String) = this(innerRule, indentType, priority, id)

//  def inner = getRule(innerId)

  override def checkSome(blocks: List[Block],
                         parentAndPosition: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
    println("checking maybe rule " + id)
    val ruleInstance = matcher.ruleInstance(parentAndPosition, this, top)
    inner.checkSome(blocks, Some(RuleParentInfo(ruleInstance, 0)), top, matcher) match {
      case Some((before, found, after)) if before.isEmpty => Some((before, ruleInstance.createMatch(found), after))
      case _ => Some((List.empty, ruleInstance.createMatch(), blocks))
    }
  }

  override def getPresetIndentType: Option[IndentType.IndentType] = indentType

  override def getPriority: Int = priority

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new MaybeRule(inner, indentType, priority, id, relations, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = {List((inner, 0))}

  override protected def addRelation(relation: RuleRelation, args: List[String]): ScalaFormattingRule =
    new MaybeRule(inner, indentType, priority, id, relations + ((relation, args)), tag)
}

object MaybeRule {
  //def apply() = new MaybeRule()
  def apply(inner: ScalaFormattingRule, id: String) = addRule(new MaybeRule(inner, None, inner.getPriority, id))
  def apply(inner: ScalaFormattingRule, indentType: IndentType.IndentType, id: String) = addRule(new MaybeRule(inner, Some(indentType), inner.getPriority, id))
}
