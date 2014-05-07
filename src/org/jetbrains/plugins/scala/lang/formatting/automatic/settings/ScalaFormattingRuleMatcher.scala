package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import com.intellij.formatting.{Alignment, Indent, WrapType}
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule._
import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule
import scala.Some
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

/**
 * @author Roman.Shein
 *         Date: 06.11.13
 */
class ScalaFormattingRuleMatcher(val rulesByNames: Map[String, ScalaFormattingRule], project: Project) {

  private var currentSettings: FormattingSettings = constructFullDefaultSettings(project)

  def getCurrentSettings = currentSettings

  private val rulesToSpacingDefiningBlocks: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlock]] = mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlock]]()

  private val topRulesToSubRules: mutable.Map[ScalaFormattingRule, List[ScalaFormattingRuleInstance]] = mutable.Map[ScalaFormattingRule, List[ScalaFormattingRuleInstance]]()

  private val blocksToRules = mutable.Map[(TextRange, IElementType), List[ScalaFormattingRuleInstance]]() //TODO: make it reference not blocks, but text ranges

  private val topRulesToMatches: mutable.Map[ScalaFormattingRule, List[RuleMatch]] = mutable.Map[ScalaFormattingRule, List[RuleMatch]]()

  private val ruleInstancesToMatches = mutable.Map[ScalaFormattingRuleInstance, List[RuleMatch]]()

  private var topBlocks = List[ScalaBlock]()

  def getRulesSettingsStringRepresentation(ruleNames: Array[String]): String = getRulesSettingsStringRepresentation(ruleNames.toList)

  def getRulesSettingsStringRepresentation(ruleNames: List[String]): String = {
    val settings = currentSettings
    val builder = new StringBuilder()
    for (ruleName <- ruleNames) {
      rulesByNames.get(ruleName) match {
        case Some(rule) => topRulesToMatches.get(rule) match {
          case Some(matches) =>
            for (ruleMatch <- matches) {
              for (ruleMatch <- matches.reverse) {
                //matches are concatenated to the beginning of list, so reverse in needed
                builder.append(ruleMatch.toStringWithSettings(settings))
              }
            }
          case None =>
        }
        case None =>
      }
    }
    builder.toString()
  }

  /**
   * Erases all data on matched blocks, but preserves current rules settings.
   */
  def reset() = {
    //TODO: obsolete this
    rulesToSpacingDefiningBlocks.clear()
    topRulesToSubRules.clear()
    blocksToRules.clear()
    topRulesToMatches.clear()
    ruleInstancesToMatches.clear()
  }

  def getFormattingRules(block: ScalaBlock) = {
    blocksToRules.getOrElse((block.getTextRange, block.getNode.getElementType), List[ScalaFormattingRuleInstance]())
  }

  def matchBlockTree(root: ScalaBlock) {
    for (rule <- rulesByNames.values) {
      matchRule(rule, root)
    }

    for (block <- root.getSubBlocks().toList if block.isInstanceOf[ScalaBlock]) {
      matchBlockTree(block.asInstanceOf[ScalaBlock])
    }
  }

  def processRuleMatch(ruleMatch: RuleMatch, subRulesMap: mutable.Map[ScalaFormattingRule, List[ScalaFormattingRuleInstance]], topRule: ScalaFormattingRule) {
    val subRulesMatches = ruleMatch.getSubRules
    val rule = ruleMatch.rule
    if (subRulesMap.get(topRule).isEmpty) {
      subRulesMap.put(topRule, List[ScalaFormattingRuleInstance]())
    }
    for ((subMatch, pos) <- subRulesMatches.zipWithIndex) {
      val rulePos = if (rule.rule.isInstanceOf[ScalaSomeRule]) 0 else subRulesMatches.size - pos - 1
      val instance = ruleInstance(Some(RuleParentInfo(rule, rulePos)), subMatch.rule.rule, topRule)
      ruleInstancesToMatches.put(instance, subMatch :: ruleInstancesToMatches.getOrElse(instance, List[RuleMatch]()))
      subMatch.getFormattingDefiningBlock match {
        case Some(formatBlock) => blocksToRules.put((formatBlock.getTextRange, formatBlock.getNode.getElementType), instance ::
                blocksToRules.getOrElse((formatBlock.getTextRange, formatBlock.getNode.getElementType), List[ScalaFormattingRuleInstance]()))
          rulesToSpacingDefiningBlocks.put(instance, formatBlock :: rulesToSpacingDefiningBlocks.getOrElse(instance, List()))
        case None =>
      }
      subRulesMap.put(topRule, instance :: subRulesMap.get(topRule).get)
      processRuleMatch(subMatch, subRulesMap, topRule)
    }
  }


  def matchRule(rule: ScalaFormattingRule, parentBlock: ScalaBlock) = {
    topBlocks = parentBlock :: topBlocks
    rule.check(parentBlock, None, rule, this) match {
      case Some(ruleMatch) /*if ruleMatch.childMatches.size > 0*/ =>
        println("rule " + rule.id + " matched")
        val instance = ruleInstance(None, rule, rule)
        ruleInstancesToMatches.put(instance, ruleMatch :: ruleInstancesToMatches.getOrElse(instance, List[RuleMatch]()))
        processRuleMatch(ruleMatch, topRulesToSubRules, rule)
        topRulesToMatches.put(rule, ruleMatch :: topRulesToMatches.getOrElse(rule, List[RuleMatch]()))
      case _ =>
    }
  }

  val instances = mutable.HashSet[ScalaFormattingRuleInstance]()

  def ruleInstance(parentAndPosition: Option[RuleParentInfo], rule: ScalaFormattingRule, top: ScalaFormattingRule) = {
    val newInstance = new ScalaFormattingRuleInstance(parentAndPosition, rule, top)
    instances.find(_ == newInstance) match {
      case Some(oldInstance) => oldInstance
      case None =>
        instances.add(newInstance)
        newInstance
    }
  }

  //-------------------------------RULE DEDUCTION SECTION--------------------------------

  def unifySpacingsAndIndents(arg1: ScalaBlockFormatterEntry, arg2: ScalaBlockFormatterEntry): Option[ScalaBlockFormatterEntry] = {

    //must use flag to determine whether arg1 is based on spacing or belongs to first child stripped of spacings

    if (arg1.ruleInstance.rule.id == "CASE CLAUSE COMPOSITE") {
      println("unifySpacingAndIndents: " + arg1 + " " + arg2 + " | " + arg1.ruleInstance.rule.id)
    }
    assert(arg1.ruleInstance.rule == arg2.ruleInstance.rule)

    //first, try to unify indents
    val indentInfo = (arg1.indentInfo, arg2.indentInfo) match {
      case (Some(indent), None) => Some(indent)
      case (None, Some(indent)) => Some(indent)
      case (Some(indent1), Some(indent2)) =>
        if (indent1 == indent2) {
          Some(indent1)
        } else {
          return None //inconsistency spotted
        }
      case (None, None) => None
    }
    //now, try to unify spacings
    val (spacing, wrap, alignment) = (arg1.spacing, arg2.spacing) match {
      case (spacing1, spacing2) if arg1.originatingFromNoSpaceChild =>
        (spacing2, false, false)
      case (spacing1, spacing2) if arg2.originatingFromNoSpaceChild =>
        (spacing1, false, false)
      case (spacing1, spacing2) if spacing1.lineBreaksCount > 0 && spacing2.lineBreaksCount == 0 =>
        //spacing1 should be wrap
        (spacing2.devour(spacing1), true, false)
      case (spacing1, spacing2) if spacing2.lineBreaksCount > 0 && spacing1.lineBreaksCount == 0 =>
        //spacing2 should be wrap
        (spacing1.devour(spacing2), true, false)
      case (spacing1, spacing2) if spacing1.lineBreaksCount == 0 && spacing2.lineBreaksCount == 0 =>
        //simple spacing
        if (spacing1.spacesCount == spacing2.spacesCount) {
          (spacing1.devour(spacing2), false, false)
        } else {
          (if (spacing1.spacesCount < spacing2.spacesCount) spacing1.devour(spacing2) else spacing2.devour(spacing1), false, true)
        }
      case (spacing1, spacing2) if spacing1.spacesCount == 0 && spacing2.spacesCount == 0 =>
        //newline-only spacing
        (if (spacing1.lineBreaksCount < spacing2.lineBreaksCount) spacing1.devour(spacing2) else spacing2.devour(spacing1),
                false, false)
      case _ => return None //inconsistency spotted
    }
    Some(new ScalaBlockFormatterEntry(spacing, indentInfo, AlignmentSetting(alignment), WrapSetting(wrap), arg1.instances ::: arg2.instances, arg1.ruleInstance))
  }

  def deduceWraps(entries: List[ScalaBlockFormatterEntry], sameFormattingBlocks: List[List[ScalaBlock]]): List[ScalaBlockFormatterEntry] = {
    var res = List[ScalaBlockFormatterEntry]()
    val wrapTypes = List[WrapType](WrapType.ALWAYS, WrapType.CHOP_DOWN_IF_LONG, WrapType.NONE, WrapType.NORMAL)
    for (entry <- entries) {
      if (entry.wrap.needWrap) {
        //for all wrap types, check if they are applicable for the entry
        for (wrapType <- wrapTypes) {
          if (entry.instances.forall(wrapTypeApplicable(_, wrapType, sameFormattingBlocks))) res = entry.setWrap(wrapType) :: res
        }
      } else {
        res = entry :: res
      }
    }
    res
  }

  def deduceAlignments(entries: List[ScalaBlockFormatterEntry], sameFormattingBlocks: List[List[ScalaBlock]], possiblyAlignedBlocks: List[List[ScalaBlock]]): List[ScalaBlockFormatterEntry] = {
    var res = List[ScalaBlockFormatterEntry]()
    /**
     * Splits a list of blocks into a list of collections such that all blocks in each collection should have the same formatting settings.
     * @param blocks
     * @param acc
     * @return
     */
    @tailrec
    def splitBlocks(blocks: List[ScalaBlock], acc: List[List[ScalaBlock]]): List[List[ScalaBlock]] = {
      if (blocks.isEmpty) acc
      else {
        val buf = sameFormattingBlocks.find(_.contains(blocks.head)).getOrElse(List[ScalaBlock](blocks.head))
        splitBlocks(blocks.tail.filterNot(buf.contains(_)), buf :: acc)
      }
    }
    for (entry <- entries) {
      if (entry.alignment.needAlignment) {
        val alignmentGroups = splitBlocks(entry.instances, List[List[ScalaBlock]]())
        //check that picked alignment group has the same alignment
        if (alignmentGroups.forall(
          alignmentGroup => alignmentGroup.isEmpty ||
                  alignmentGroup.tail.forall(_.getInLineOffset == alignmentGroup.head.getInLineOffset)
        )) res = entry :: res
      } else {
        res = entry :: res
      }
    }
    res
  }

  /**
   * Gets a copy of default rule settings, it must be a copy, i.e. modifications to returned map should not alter the defaults.
   * @return
   */
  def getDefaultSettings: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]] = //TODO: implement default settings
    mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]()

  def constructFullDefaultSettings(project: Project): FormattingSettings = {
    val serializer = ScalaFormattingSettingsSerializer.getInstance(project)
    if (serializer == null) return new FormattingSettings(None, None, Map())
    val serialized = serializer.getInstance
    serialized.getOrElse(new FormattingSettings(None, None, Map())) //TODO: implement default settings
  }

  /**
   *
   * @param rules a list of single rules to process (filter off settings that contradict for different blocks matched with this rule)
   * @param settings maps rules to list of entries (block matched by the rule, list of possible formatter entries with known spacing size and indent size)
   * @return maps rules to list of possible formatter entries with known spacing size and indent size and flag signalling whether alignment or wrap are needed
   */
  def processSingleRules(rules: List[ScalaFormattingRuleInstance], settings: mutable.Map[ScalaFormattingRuleInstance, List[(ScalaBlock, List[ScalaBlockFormatterEntry])]],
                         sameFormattingBlocks: List[List[ScalaBlock]], possiblyAlignedBlocks: List[List[ScalaBlock]]):
  mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]] = {
    val res = getDefaultSettings
    for (ruleInstance <- rules) {
      settings.get(ruleInstance) match {
        case Some(blockRulePossibleSettings) if blockRulePossibleSettings.nonEmpty =>
          var checkedEntries = blockRulePossibleSettings.head._2.toSet
          for ((block, blockPossibleEntries) <- blockRulePossibleSettings.tail) {
            println("block " + block.getNode.getText())
            var newCheckedSettings = List[ScalaBlockFormatterEntry]()
            for (blockPossibleEntry <- blockPossibleEntries) {
              for (compareBlockPossibleEntry <- checkedEntries) {
                unifySpacingsAndIndents(blockPossibleEntry, compareBlockPossibleEntry) match {
                  case None => println("Conflict on spacing and indents unification.") //TODO: LOG THE CONFLICT SOMEHOW
                  case Some(checkSetting) =>
                    println("Unification successful")
                    newCheckedSettings = checkSetting :: newCheckedSettings
                }
              }
            }
            checkedEntries = newCheckedSettings.toSet
          }
          res.put(ruleInstance, deduceAlignments(deduceWraps(checkedEntries.toList, sameFormattingBlocks), sameFormattingBlocks, possiblyAlignedBlocks))
        case _ => //there were no blocks matched with this block rule, use defaults
      }
    }
    res
  }

  /**
   * Takes settings with unified spacings, alignments and wraps,
   * @param rulesToSettings
   * @return
   */
  def deduceIndentSettings(rulesToSettings: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]): List[FormattingSettings] = {
    def getWhitespaces(rule: ScalaFormattingRuleInstance): List[TextRange] = {
      rulesToSpacingDefiningBlocks.get(rule) match {
        case None => List()
        case Some(blocks) => blocks.map(_.getInitialSpacing).filter(_.isDefined).map(_.get.getTextRange)
      }
    }

    val ruleToIndentSettings = mutable.Map[ScalaFormattingRuleInstance, List[FormattingSettings]]()
    //for every rule, build a list of possible indent size settings
    for (rule <- rulesToSettings.keySet) {
      ruleToIndentSettings.put(rule, FormattingSettings.buildSettings(rule, rulesToSettings.get(rule).get, getWhitespaces(rule)))
    }

    if (ruleToIndentSettings.isEmpty) {
      List[FormattingSettings]()
    } else {
      //now, unify all settings
      var currentSettings = ruleToIndentSettings.get(ruleToIndentSettings.keys.toList.head).get
      for (rule <- ruleToIndentSettings.keys.toList.tail) {
        for (setting <- ruleToIndentSettings.get(rule).get) {
          var newCurrentSettings = List[FormattingSettings]()
          for (currentSetting <- currentSettings) {
            currentSetting.unify(setting, this) match {
              case None =>
              case Some(newSetting) => newCurrentSettings = newSetting :: newCurrentSettings
            }
          }
          currentSettings = newCurrentSettings
        }
      }
      currentSettings
    }
  }

  def unifyClusterSpacingsAndIndents(formattingCluster: Set[ScalaFormattingRuleInstance],
                                     settings: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]): List[ScalaBlockFormatterEntry] = {
    if (formattingCluster.isEmpty) return List()
    val listCluster = formattingCluster.toList //TODO: this is a hack, fix it
    var checkedSettings = settings.getOrElse(listCluster.head, List())
    for (clusterRule <- listCluster.tail) {
      for (clusterSetting <- settings.get(clusterRule).getOrElse(List())) {
        var newCheckedSettings = List[ScalaBlockFormatterEntry]()
        for (compareSetting <- checkedSettings) {
          unifySpacingsAndIndents(clusterSetting, compareSetting) match {
            case None => println("Conflict in ensureSamePossibilities.") //TODO: LOG THE CONFLICT SOMEHOW
            case Some(checkSetting) => newCheckedSettings = checkSetting :: newCheckedSettings
          }
        }
        checkedSettings = newCheckedSettings
      }
    }
    checkedSettings
  }

  def getSameFormattingClusters: List[Set[ScalaFormattingRuleInstance]] = {
    val res = mutable.HashMap[ScalaFormattingRule.Anchor, Set[ScalaFormattingRuleInstance]]()
    for (ruleInstance <- instances) {
      ruleInstance.rule.anchor.map(
        (anchor) => res.put(anchor, res.getOrElse(anchor, Set[ScalaFormattingRuleInstance]()) + ruleInstance)
      )
    }
    res.values.toList
  }

  def getPossiblyAlignedClusters: List[Set[ScalaFormattingRuleInstance]] = {
    val res = mutable.HashMap[String, Set[ScalaFormattingRuleInstance]]()
    for (ruleInstance <- instances) {
      ruleInstance.rule.alignmentAnchor.map(
        (anchor) => res.put(anchor, res.getOrElse(anchor, Set[ScalaFormattingRuleInstance]()) + ruleInstance)
      )
    }
    res.values.toList
  }

  def getBlocksFromClusters(clusters: List[Set[ScalaFormattingRuleInstance]]): List[List[ScalaBlock]] = {
    var res = List[List[ScalaBlock]]()
    for (cluster <- clusters) {
      var blocks = List[ScalaBlock]()
      for (clusterRule <- cluster) {
        blocks = ruleInstancesToMatches.getOrElse(clusterRule, List()).map(_.formatBlock).filter(_.isDefined).map(_.get) :::
                blocks
      }
      res = blocks :: res
    }
    res
  }

  //On input: unprocessed data on spacings before all blocks
  //On output: list of possible formatting settings for all rules or identification of a conflict
  /**
   *
   * @return
   */
  def deriveSettings: FormattingSettings = {
    val rulesToEntries = mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]()
    val processedRules = mutable.Set[ScalaFormattingRuleInstance]()

    val sameFormattingClusters = getSameFormattingClusters
    val possiblyAlignedClusters = getPossiblyAlignedClusters
    val sameFormattingBlocks: List[List[ScalaBlock]] = getBlocksFromClusters(sameFormattingClusters)
    val possiblyAlignedBlocks: List[List[ScalaBlock]] = getBlocksFromClusters(possiblyAlignedClusters)

    //now every spacing belongs only to one ruleInstance
    for ((topRule, childRules) <- topRulesToSubRules) {
      val possibleSettings = mutable.Map[ScalaFormattingRuleInstance, List[(ScalaBlock, List[ScalaBlockFormatterEntry])]]()
      var rulesToUnifyBlocksFor = List[ScalaFormattingRuleInstance]()
      for (child <- childRules if !processedRules.contains(child)) {
        var possibleRuleSettings = List[(ScalaBlock, List[ScalaBlockFormatterEntry])]()
        for (matchedBlock <- rulesToSpacingDefiningBlocks.getOrElse(child, List())) {
          possibleRuleSettings = (matchedBlock, getPossibleSpacingSettings(matchedBlock, child)) :: possibleRuleSettings
        }
        possibleSettings.put(child, possibleRuleSettings)
        //mark the rule so that we don't try to deduce entries for it multiple times
        processedRules.add(child)
        //add the rule into a block settings unification queue (notice that since the rule has just been marked as processed, block settings unification will be performed only once)
        rulesToUnifyBlocksFor = child :: rulesToUnifyBlocksFor
      }
      //make entries for blocks within single rule consistent
      val singleRulesProcessed = processSingleRules(rulesToUnifyBlocksFor, possibleSettings, sameFormattingBlocks, possiblyAlignedBlocks)
      for (entry <- singleRulesProcessed) {
        rulesToEntries += entry
      }
    }

    //extract clusters of rules that should have the same formatting
    for (formattingCluster <- sameFormattingClusters) {
      //ensure that only entries consistent for the whole cluster are present
      val entries = deduceAlignments(deduceWraps(unifyClusterSpacingsAndIndents(formattingCluster, rulesToEntries), sameFormattingBlocks), sameFormattingBlocks, possiblyAlignedBlocks)
      if (formattingCluster.exists(rulesToEntries.contains(_))) {
        for (ruleInstance <- formattingCluster) {
          rulesToEntries.put(ruleInstance, entries)
        }
      }
    }

    //deduce indent size settings
    val indentTypeSettings = deduceIndentSettings(rulesToEntries)

    //TODO: some way to choose from multiple possible settings

    if (indentTypeSettings.isEmpty) {
      null
    } else {
      currentSettings = indentTypeSettings.tail.foldLeft(indentTypeSettings.head)((setting: FormattingSettings, acc: FormattingSettings) => if (setting.rulesCovered > acc.rulesCovered) acc else setting)
      currentSettings
    }
  }

  def wrapTypeApplicable(block: ScalaBlock, wrapType: WrapType, sameFormattingBlocks: List[List[ScalaBlock]]): Boolean = {
    //TODO: precalculate settings for clusters
    val blockCluster = sameFormattingBlocks.find(_.contains(block)).getOrElse(List(block))
    wrapType match {
      case WrapType.ALWAYS =>
        blockCluster.forall(_.isOnNewLine)
      case WrapType.CHOP_DOWN_IF_LONG =>
        //either current block does not need wrap or all the other blocks must be wrapped as well
        blockCluster.forall(!_.wouldCrossRightMargin) || blockCluster.forall(_.isOnNewLine)
      case WrapType.NORMAL =>
        blockCluster.forall((block) => !block.wouldCrossRightMargin || block.isOnNewLine)
      //        !block.crossesRightMargin || block.isOnNewLine
      case WrapType.NONE =>
        blockCluster.forall((block) => !block.wouldCrossRightMargin || !block.isOnNewLine)
      //        !block.crossesRightMargin || !block.isOnNewLine
    }
  }

  def convertSettings(settings: ScalaCodeStyleSettings, commonSettings: CommonCodeStyleSettings): FormattingSettings = {
    val ruleInstanceToEntry = mutable.Map[ScalaFormattingRuleInstance, ScalaBlockFormatterEntry]()
    def modifyEntry(instance: ScalaFormattingRuleInstance, modifier: (ScalaBlockFormatterEntry => ScalaBlockFormatterEntry)) = {
      ruleInstanceToEntry.put(instance, modifier(ruleInstanceToEntry.getOrElse(instance, ScalaBlockFormatterEntry(instance))))
    }
//    def setMinLineFeeds(count: Int)(entry: ScalaBlockFormatterEntry) = entry.setSpacing(entry.spacing.setMinLineFeeds(count))

    def setLineFeeds(count: Int)(entry: ScalaBlockFormatterEntry) = entry.setSpacing(entry.spacing.setLineFeeds(count))

    //for every settings, build a dummy match and then try to unify them all (i.e. derive settings)
    val ALIGN_IF_ELSE_IF_instance = ruleInstancesByTags.get(ALIGN_IF_ELSE_TAG_IF_WORD)
    val ALIGN_IF_ELSE_ELSE_instance = ruleInstancesByTags.get(ALIGN_IF_ELSE_TAG_ELSE_WORD)
    (ALIGN_IF_ELSE_IF_instance, ALIGN_IF_ELSE_ELSE_instance) match {
      case (Some(ifInstance), Some(elseInstance)) =>
        modifyEntry(ifInstance, _.setAlignment(settings.ALIGN_IF_ELSE))
        modifyEntry(elseInstance, _.setAlignment(settings.ALIGN_IF_ELSE))
    }
    if (ALIGN_IF_ELSE_ELSE_instance.isDefined) {
      modifyEntry(ALIGN_IF_ELSE_ELSE_instance.get, if (commonSettings.ELSE_ON_NEW_LINE) setLineFeeds(1) else setLineFeeds(0))
    }
    val whileWordInstance = ruleInstancesByTags.get(whileWordTag)
    if (whileWordInstance.isDefined) {
      modifyEntry(whileWordInstance.get, if (commonSettings.WHILE_ON_NEW_LINE) setLineFeeds(1) else setLineFeeds(0))
    }
    val catchWordInstance = ruleInstancesByTags.get(catchWordTag)
    if (catchWordInstance.isDefined) {
      modifyEntry(catchWordInstance.get, if (commonSettings.CATCH_ON_NEW_LINE) setLineFeeds(1) else setLineFeeds(0))
    }
    val finallyWordInstance = ruleInstancesByTags.get(finallyWordTag)
    if (finallyWordInstance.isDefined) {
      modifyEntry(finallyWordInstance.get, if (commonSettings.FINALLY_ON_NEW_LINE) setLineFeeds(1) else setLineFeeds(0))
    }


    //TODO: finish and test me
    null
  }

  private val ruleInstancesByTags = mutable.Map[String, ScalaFormattingRuleInstance]()

  /**
   * Fills ruleInstances and ruleInstancesByTags with dummy instances constructed from rules. Also
   * @param topRules
   */
  def buildRuleInstances(topRules: List[ScalaFormattingRule]) {
    for (rule <- topRules) {
      buildRuleInstances(rule, rule, None)
    }
  }

  def buildRuleInstances(rule: ScalaFormattingRule, root: ScalaFormattingRule, parentInfo: Option[RuleParentInfo]) {
    val instance = ruleInstance(parentInfo, rule, root)
    rule.tag.map(ruleInstancesByTags.put(_, instance))
    //now, process all the child rules
    val children = rule.childrenWithPosition
    for ((child, position) <- children) {
      buildRuleInstances(child, root, Some(RuleParentInfo(instance, position)))
    }
  }
}

object ScalaFormattingRuleMatcher {

  def getDefaultMatcher(learnRoot: ScalaBlock): ScalaFormattingRuleMatcher = {
    val matcher = new ScalaFormattingRuleMatcher(topRulesByIds, learnRoot.getNode.getPsi.getProject)
    matcher.matchBlockTree(learnRoot)
    matcher.deriveSettings
    matcher.reset()
    matcher
  }

  val topRulesByIds: Map[String, ScalaFormattingRule] =
  //first, construct default rules
    Map[String, ScalaFormattingRule](
      rule.whileDefault.id -> rule.whileDefault,
      rule.ifDefault.id -> rule.ifDefault,
      rule.caseClausesComposite.id -> rule.caseClausesComposite,
      rule.tryDefault.id -> rule.tryDefault,
      rule.matchRule.id -> rule.matchRule,
      rule.idChainDefault.id -> rule.idChainDefault
//      rule.callChainDefault.id -> rule.callChainDefault
    )
  //Map[String, ScalaFormattingRule](rule.caseClausesComposite.id -> rule.caseClausesComposite)

  private def runMatcher(rootBlock: ScalaBlock) = {
    val matcher = new ScalaFormattingRuleMatcher(topRulesByIds, rootBlock.getNode.getPsi.getProject)
    matcher.matchBlockTree(rootBlock)
    matcher
  }

  def test(rootBlock: ScalaBlock) = {
    val settings = runMatcher(rootBlock).deriveSettings

    settings
  }

  def testExtraction(rootBlock: ScalaBlock, rulesNames: Array[String], project: Project): String = {
    val matcher = runMatcher(rootBlock)
    matcher.deriveSettings
    matcher.getRulesSettingsStringRepresentation(rulesNames)
  }

  def testRuleMatch(rootBlock: ScalaBlock, rulesNames: Array[String]): String = {
    val matcher = runMatcher(rootBlock)
    val res = new StringBuilder()
    for (ruleName <- rulesNames) {
      matcher.rulesByNames.get(ruleName) match {
        case Some(rule) =>
          matcher.topRulesToMatches.get(rule) match {
            case Some(matches) =>
              for (ruleMatch <- matches.reverse) {
                //matches are concatenated to the beginning of list, so reverse in needed
                res.append(ruleMatch.toString)
              }
            case None =>
          }
        case None => throw new IllegalArgumentException("Rule to be tested not found.")
      }
    }

    res.toString()
  }
}