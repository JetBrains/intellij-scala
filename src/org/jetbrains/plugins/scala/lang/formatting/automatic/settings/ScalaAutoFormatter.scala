package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.{RuleMatch, ScalaBlockRule, ScalaFormattingRule}
import com.intellij.formatting._
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaSpacingProcessor
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation


/**
 * @author Roman.Shein
 *         Date: 22.11.13
 */
class ScalaAutoFormatter(matcher: ScalaFormattingRuleMatcher) {

  //  private val anchorToAlignment = mutable.HashMap[ScalaFormattingRule.Anchor, Alignment]()

  private val topMatchToAlignmentData = mutable.Map[RuleMatch, mutable.Map[RuleRelation, Alignment]]()

  private def getDefaultSpacing = Spacing.getReadOnlySpacing //Spacing.createSpacing(0, 0, 0, false, 0)//ScalaSpacingProcessor.COMMON_SPACING//

  private def getDefaultIndent = Indent.getNoneIndent

  private def getDefaultAlignment = null

  private def getDefaultWrap = Wrap.createWrap(WrapType.NONE, false)

  def chooseFormattingRule(rules: List[ScalaFormattingRuleInstance]): ScalaFormattingRuleInstance = rules.head //TODO: more sophisticated strategy based on RuleInstance depth in root rule

  def chooseEntry(entries: List[ScalaBlockFormatterEntry]): ScalaBlockFormatterEntry = entries.head //TODO: more sophisticated strategy

  def getSpacing(blockLeft: Block, blockRight: Block): Spacing = {
    if (!blockRight.isInstanceOf[ScalaBlock]) return null
    val rules = matcher.getFormattingRules(blockRight.asInstanceOf[ScalaBlock])
    if (rules.isEmpty) {
      getDefaultSpacing
    } else {
      matcher.getCurrentSettings.instances.get(chooseFormattingRule(rules)) match {
        case Some(entries) if !entries.isEmpty =>
          //TODO: implement LFDependent spacing once the rules contain info on blocks to search dependencies with
          val spacingInfo = chooseEntry(entries).spacing
          //TODO: cache spacing instances to avoid creating multiple Spacing objects with the same parameters
          Spacing.createSpacing(spacingInfo.spacesCount, spacingInfo.spacesCount, spacingInfo.minLineBreaksCount.getOrElse(spacingInfo.lineBreaksCount), spacingInfo.maxLineBreaksCount.getOrElse(spacingInfo.lineBreaksCount) > 0, spacingInfo.maxLineBreaksCount.getOrElse(spacingInfo.lineBreaksCount))
        case _ => getDefaultSpacing
      }
    }
  }

  def getWrap(block: ScalaBlock): Wrap = {
    val rules = matcher.getFormattingRules(block)
    if (rules.isEmpty) {
      getDefaultWrap
    } else {
      matcher.getCurrentSettings.instances.get(chooseFormattingRule(rules)) match {
        case Some(entries) if !entries.isEmpty =>
          val wrapSetting = chooseEntry(entries).wrap
          if (wrapSetting.wrapDefined) {
            wrapSetting.wrapType match {
              case Some(wrapType) => Wrap.createWrap(wrapType, false) //TODO: determine the second parameter
              case None => null
            }
          } else Wrap.createWrap(WrapType.NONE, false)
        case _ => null
      }
    }
  }

  def getAlignment(block: ScalaBlock): Alignment = {
    val rules = matcher.getFormattingRules(block)
    if (rules.isEmpty) {
      getDefaultAlignment
    } else {
      val ruleInstance = chooseFormattingRule(rules)
      matcher.getCurrentSettings.instances.get(ruleInstance) match {
        case Some(entries) if !entries.isEmpty =>
          val alignmentSetting = chooseEntry(entries).alignment
          if (alignmentSetting.needAlignment) {
            matcher.getTopMatch(block, ruleInstance) match {
              case Some(topMatch) =>
                val aligningRelations = ruleInstance.rule.relations.map(_._1).filter(_.isAlignedByThisRelation(ruleInstance))
                val alignmentMap = topMatchToAlignmentData.get(topMatch) match {
                  case Some(aMap) => aMap
                  case None =>
                    val newMap = mutable.Map[RuleRelation, Alignment]()
                    topMatchToAlignmentData.put(topMatch, newMap)
                    newMap
                }
                val alignment = aligningRelations.find(relation => alignmentMap.contains(relation)) match {
                  case Some(aligningRelation) =>
                    //there was some alignment for this relation in current top rule match
                    alignmentMap.get(aligningRelation).get
                  case None =>
                    //there was no alignment for this relation in current top rule match
                    Alignment.createAlignment(true)
                }
                for (relation <- aligningRelations) {
                  if (alignmentMap.contains(relation)) {
                    assert(alignmentMap.get(relation).get == alignment)
                  } else {
                    alignmentMap.put(relation, alignment)
                  }
                }
                alignment
              case _ => null
            }
          } else null
        case _ => null
      }
    }
  }

  def getIndent(block: ScalaBlock): Indent = {
    val rules = matcher.getFormattingRules(block)
    if (rules.isEmpty) {
      getDefaultIndent
    } else {
      matcher.getCurrentSettings.instances.get(chooseFormattingRule(rules)) match {
        case Some(entries) if !entries.isEmpty =>
          val indentEntry = chooseEntry(entries)
          if (indentEntry.originatingFromNoSpaceChild) Indent.getNoneIndent else
          indentEntry.indentInfo match {
            case Some(indentInfo) => indentInfo.indentType match {
              case Some(IndentType.ContinuationIndent) => Indent.getContinuationIndent(indentInfo.indentRelativeToDirectParent)
              case Some(IndentType.NormalIndent) => Indent.getNormalIndent(indentInfo.indentRelativeToDirectParent)
              case _ => Indent.getSpaceIndent(indentInfo.indentLength, indentInfo.indentRelativeToDirectParent)
            }
            case None => getDefaultIndent
          }
        case _ => getDefaultIndent
      }
    }
  }

  def runMatcher(rootBlock: ScalaBlock) = matcher.matchBlockTree(rootBlock)
}

object ScalaAutoFormatter {
}
