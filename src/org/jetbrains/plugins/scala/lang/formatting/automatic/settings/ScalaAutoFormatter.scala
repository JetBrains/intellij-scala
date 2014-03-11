package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.{ScalaBlockRule, ScalaFormattingRule}
import com.intellij.formatting._
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaSpacingProcessor
import scala.collection.mutable


/**
 * @author Roman.Shein
 *         Date: 22.11.13
 */
class ScalaAutoFormatter(matcher: ScalaFormattingRuleMatcher) {

  private val anchorToAlignment = mutable.HashMap[ScalaFormattingRule.Anchor, Alignment]()

  private def getDefaultSpacing = Spacing.getReadOnlySpacing//Spacing.createSpacing(0, 0, 0, false, 0)//ScalaSpacingProcessor.COMMON_SPACING//

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
          if (wrapSetting.needWrap) {
            wrapSetting.wrapType match {
              case Some(wrapType) => Wrap.createWrap(wrapType, false) //TODO: determine the second parameter
              case None => null
            }
          } else null
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
        case Some(entries) if !entries.isEmpty  =>
          val alignmentSetting = chooseEntry(entries).alignment
          if (alignmentSetting.needAlignment) {
            val anchor = ruleInstance.rule.anchor
            assert(anchor.isDefined)
            if (!anchorToAlignment.contains(anchor.get)) {
              anchorToAlignment.put(anchor.get, Alignment.createAlignment(true))
            }
            anchorToAlignment.get(anchor.get).get
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
          chooseEntry(entries).indentInfo match {
            case Some(indentInfo) => indentInfo.indentType match {
              case Some(IndentType.ContinuationIndent) => Indent.getContinuationIndent(indentInfo.indentRelativeToDirectParent)
              case Some(IndentType.NormalIndent) => Indent.getNormalIndent(indentInfo.indentRelativeToDirectParent)
              case None => Indent.getSpaceIndent(indentInfo.indentLength, indentInfo.indentRelativeToDirectParent)
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
  //  def getBlockTreeSettings(topBlock: ScalaBlock, rules: Map[String, ScalaFormattingRule]): IndentTypeSettings = {
  //    val matcher = new ScalaFormattingRuleMatcher(rules)
  //
  //    matchBlocks(topBlock, matcher, rules)
  //
  //    matcher.deriveSettings
  //  }

  //  private def matchBlocks(parentBlock: ScalaBlock, matcher: ScalaFormattingRuleMatcher, rules: List[ScalaFormattingRule]) {
  //    import scala.collection.JavaConversions._
  //
  //    //match all rules
  //    for (rule <- rules) {
  //      matcher.matchRule(rule, parentBlock)
  //    }
  //
  //    for (childBlock: Block <- parentBlock.getSubBlocks().toList if childBlock.isInstanceOf[ScalaBlock]) {
  //      matchBlocks(childBlock.asInstanceOf[ScalaBlock], matcher, rules)
  //    }
  //  }

}
