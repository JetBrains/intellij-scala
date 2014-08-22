package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings.matching

import com.intellij.formatting.WrapType
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule._
import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.{IndentTypeRelation, RuleRelation}
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.serialization.{ExampleBase, ScalaFormattingSettingsSerializer}
import org.jdom.input.SAXBuilder
import java.io.File
import org.jetbrains.plugins.scala.lang.formatting.automatic.AutoFormattingUtil.wrapInReadAction
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.tree.FormattingSettingsTree

/**
 * @author Roman.Shein
 *         Date: 06.11.13
 */
class ScalaFormattingRuleMatcher(val rulesByNames: Map[String, ScalaFormattingRule], project: Option[Project]) {

  private var currentSettings: FormattingSettings = constructFullDefaultSettings(project)

  private var exampleBase: Option[ExampleBase] = None

  def getCurrentSettings = currentSettings

  def getTopMatch(block: ScalaBlock, ruleInstance: ScalaFormattingRuleInstance): Option[RuleMatch] = {
    var currentInstance = ruleInstance
    val textRange = block.getTextRange
    while (currentInstance.parentAndPosition.isDefined) {
      currentInstance= currentInstance.parent.get
    }
    val matches = ruleInstancesToMatches.getOrElse(currentInstance, List()).filter(ruleMatch =>
      topMatchesToTextRanges.get(ruleMatch) match {
        case Some(res) =>
          res.contains(textRange)
        case None => false
      }
    )
    if (matches.isEmpty) {
      None
    } else {
      Some(matches.min(Ordering.by((ruleMatch: RuleMatch) => {
        val range = topMatchesToTextRanges.get(ruleMatch).get
        range.getEndOffset - range.getStartOffset
      }))
      )
    }
  }

  private val rulesToSpacingDefiningBlocks: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlock]] = mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlock]]()

  private val topRulesToSubRules: mutable.Map[ScalaFormattingRule, List[ScalaFormattingRuleInstance]] = mutable.Map[ScalaFormattingRule, List[ScalaFormattingRuleInstance]]()

  private val blocksToRules = mutable.Map[(TextRange, IElementType), List[ScalaFormattingRuleInstance]]() //TODO: make it reference not blocks, but text ranges

  private val topRulesToMatches: mutable.Map[ScalaFormattingRule, List[RuleMatch]] = mutable.Map[ScalaFormattingRule, List[RuleMatch]]()

  private val ruleInstancesToMatches = mutable.Map[ScalaFormattingRuleInstance, List[RuleMatch]]()

  private val topMatchesToTextRanges = mutable.Map[RuleMatch, TextRange]()

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

  def matchAroundBlock(block: ScalaBlock, matchLogMap: Option[mutable.Map[ScalaFormattingRule, List[ScalaBlock]]],
                              needWrapInReadAction: Boolean, missingBlocksData: MissingBlocksData*) {
    for (rule <- rulesByNames.values) {
      if (needWrapInReadAction) {
        wrapInReadAction {matchRule(rule, block, matchLogMap, missingBlocksData:_*)}
      } else {
        matchRule(rule, block, matchLogMap, missingBlocksData:_*)
      }
    }

    if (block.myParentBlock != null) {
      matchAroundBlock(block.myParentBlock, matchLogMap, needWrapInReadAction, missingBlocksData:_*)
    }
  }

  def matchBlockTree(root: ScalaBlock, matchLogMap: Option[mutable.Map[ScalaFormattingRule, List[ScalaBlock]]] = None,
                     needWrapInReadAction: Boolean = false) {
//    println("match block tree on block\n" + root.getNode.getText)
    for (rule <- rulesByNames.values) {
      if (needWrapInReadAction) {
        wrapInReadAction {matchRule(rule, root, matchLogMap)}
      } else {
        matchRule(rule, root, matchLogMap)
      }

    }

    for (block <- root.getSubBlocks().toList if block.isInstanceOf[ScalaBlock]) {
      matchBlockTree(block.asInstanceOf[ScalaBlock], matchLogMap, needWrapInReadAction)
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


  def matchRule(rule: ScalaFormattingRule,
                parentBlock: ScalaBlock,
                missingBlocks: MissingBlocksData*) {matchRule(rule, parentBlock, None, missingBlocks:_*)}

  def matchRule(rule: ScalaFormattingRule,
                parentBlock: ScalaBlock,
                matchLogMap: Option[mutable.Map[ScalaFormattingRule, List[ScalaBlock]]],
                missingBlocks: MissingBlocksData*) {
    topBlocks = parentBlock :: topBlocks
    rule.check(parentBlock, None, rule, this, missingBlocks:_*) match {
      case Some(ruleMatch) /*if ruleMatch.childMatches.size > 0*/ =>
//        println("rule " + rule.id + " matched")
        matchLogMap match {
          case Some(map) => map.put(rule, parentBlock :: map.getOrElse(rule, List()))
          case None =>
        }
        val instance = ruleInstance(None, rule, rule)
        ruleInstancesToMatches.put(instance, ruleMatch :: ruleInstancesToMatches.getOrElse(instance, List[RuleMatch]()))
        topMatchesToTextRanges.put(ruleMatch, parentBlock.getTextRange)
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

  /**
   * Given two formatter entries for the same rule, tries to create a new formatter entry that hase spacing and
   * indents settings that agree to (i.e. are more general then) both arguments. Also detects whether alignment or wrap
   * should be used to explain spacings that are present. (by default, presumes no alignment or wrap usage)
   * @param arg1 first formatter entry
   * @param arg2 second formatter entry
   * @return unifying entry, if it is possible to build one, nothing otherwise
   */
  def unifySpacingsAndIndents(arg1: ScalaBlockFormatterEntry,
                              arg2: ScalaBlockFormatterEntry,
                              failLogger: Option[SettingDeductionFailLogger] = None,
                              unifyIndentsOnly: Boolean = false): List[ScalaBlockFormatterEntry] = {

    //must use flag to determine whether arg1 is based on spacing or belongs to first child stripped of spacings

//    println(arg1 + "\n----------------------------------\n" + arg2 + "\n----------------------------------")

    assert(arg1.rule == arg2.rule)

    var res = List[ScalaBlockFormatterEntry]()

    //first, try to unify indents
    val indentInfo = (arg1.indentInfo, arg2.indentInfo) match {
      case (Some(indent), None) => Some(indent)
      case (None, Some(indent)) => Some(indent)
      case (Some(indent1), Some(indent2)) =>
        if (indent1 == indent2) {
          Some(indent1)
        } else {
          failLogger match {
            case Some(logger) =>
              logger.logEntryUnificationFail(arg1, arg2, "indent unification failed")
            case None =>
          }
          return List() //inconsistency spotted
        }
      case (None, None) => None
    }

    if (unifyIndentsOnly && indentInfo.isDefined) {
      return List(arg1)
    }

    def buildEntry(spacing: SpacingInfo, wrap: Boolean, alignment: Boolean) =
      new ScalaBlockFormatterEntry(spacing,
        indentInfo,
        AlignmentSetting(alignment),
        WrapSetting(wrap),
        arg1.instances ::: arg2.instances,
        arg1.rule)

    //now, try to unify spacings
    val (spacing1, spacing2) = (arg1.spacing, arg2.spacing)
    (arg1.spacing.spacesCount, arg2.spacing.spacesCount, arg1.spacing.lineBreaksCount, arg2.spacing.lineBreaksCount) match {
      case _ if arg1.originatingFromNoSpaceChild =>
        res = buildEntry(spacing2, arg2.wrap.wrapDefined, arg2.alignment.needAlignment) :: res
      case _ if arg2.originatingFromNoSpaceChild =>
        res = buildEntry(spacing1, arg1.wrap.wrapDefined, arg1.alignment.needAlignment) :: res
      case (_, _, breaks1, 0) if breaks1 > 0 =>
        //spacing1 should be wrap
        res = buildEntry(spacing2.devour(spacing1), wrap = true, alignment = false) :: res
        res = buildEntry(spacing2.devour(spacing1), wrap = true, alignment = true) :: res
      case (_, _, 0, breaks2) if breaks2 > 0 =>
        //spacing2 should be wrap
        res = buildEntry(spacing1.devour(spacing2), wrap = true, alignment = false) :: res
        res = buildEntry(spacing1.devour(spacing2), wrap = true, alignment = true) :: res
      case (length1, length2, 0, 0) =>
        //simple spacing
        if (spacing1.spacesCount == spacing2.spacesCount) {
          res = buildEntry(
            spacing1.devour(spacing2),
            arg1.wrap.wrapDefined || arg2.wrap.wrapDefined,
            arg1.alignment.needAlignment || arg2.alignment.needAlignment
          ) :: res
        } else {
          res = buildEntry(
            if (spacing1.spacesCount < spacing2.spacesCount) spacing1.devour(spacing2) else spacing2.devour(spacing1),
            arg1.wrap.wrapDefined || arg2.wrap.wrapDefined,
            alignment = true
          ) :: res
        }
      case (0, 0, breaks1, breaks2) =>
        //newline-only spacing
        val wrapVerified = arg1.wrap.wrapDefined || arg2.wrap.wrapDefined
        res = buildEntry(
          if (spacing1.lineBreaksCount < spacing2.lineBreaksCount) spacing1.devour(spacing2)
          else spacing2.devour(spacing1),
          wrapVerified,
          arg1.alignment.needAlignment || arg2.alignment.needAlignment) :: res
        if (!wrapVerified) {
          res = buildEntry(
            if (spacing1.lineBreaksCount < spacing2.lineBreaksCount) spacing1.devour(spacing2)
            else spacing2.devour(spacing1),
            true,
            arg1.alignment.needAlignment || arg2.alignment.needAlignment) :: res
        }
      case _ =>
        failLogger match {
          case Some(logger) =>
            logger.logEntryUnificationFail(arg1, arg2, "spacings unification failed")
          case None =>
        }
        return List() //inconsistency spotted
    }
//    res = new ScalaBlockFormatterEntry(spacing, indentInfo, AlignmentSetting(alignment), WrapSetting(wrap), arg1.instances ::: arg2.instances, arg1.rule) :: res
//    for (temp <- res) {
//      println(temp)
//    }
//    println("++++++++++++++++++++++")
    res
  }


  /**
   * Gets a copy of default rule settings, it must be a copy, i.e. modifications to returned map should not alter the defaults.
   * @return
   */
  def getDefaultSettings: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]] = //TODO: implement default settings
    mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]()

  def constructFullDefaultSettings(project: Option[Project]): FormattingSettings = {
    project match {
      case Some(myProject) =>
        val serializer = ScalaFormattingSettingsSerializer.getInstance(myProject)
        if (serializer == null) return new FormattingSettings(None, None, Map())
        val serialized = serializer.getInstance
        serialized.getOrElse(new FormattingSettings(None, None, Map())) //TODO: implement default settings
      case None => new FormattingSettings(None, None, Map())
    }
  }

  /**
   *
   * @param rules a list of single rules to process (filter off settings that contradict for different blocks matched with this rule)
   * @param settings maps rules to list of entries (block matched by the rule, list of possible formatter entries with known spacing size and indent size)
   * @return maps rules to list of possible formatter entries with known spacing size and indent size and flag signalling whether alignment or wrap are needed
   */
  def processSingleRules(rules: List[ScalaFormattingRuleInstance],
                         settings: mutable.Map[ScalaFormattingRuleInstance, List[(ScalaBlock, List[ScalaBlockFormatterEntry])]],
                         failLogger: Option[SettingDeductionFailLogger] = None,
                         needWrapInReadAction: Boolean,
                         processIndentsOnly: Boolean):
  mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]] = {
    val res = getDefaultSettings
    for (ruleInstance <- rules) {
      settings.get(ruleInstance) match {
        case Some(blockRulePossibleSettings) if blockRulePossibleSettings.nonEmpty =>
          var checkedEntries = blockRulePossibleSettings.head._2.toSet
          for ((block, blockPossibleEntries) <- blockRulePossibleSettings) {
            var newCheckedSettings = List[ScalaBlockFormatterEntry]()
            for (blockPossibleEntry <- blockPossibleEntries) {
              for (compareBlockPossibleEntry <- checkedEntries) {
                if (needWrapInReadAction) {
                  wrapInReadAction {
                    newCheckedSettings =
                            unifySpacingsAndIndents(blockPossibleEntry, compareBlockPossibleEntry, failLogger, processIndentsOnly) :::
                                    newCheckedSettings
                  }
                } else {
                  newCheckedSettings =
                          unifySpacingsAndIndents(blockPossibleEntry, compareBlockPossibleEntry, failLogger, processIndentsOnly) :::
                                  newCheckedSettings
                }
              }
            }
            checkedEntries = newCheckedSettings.toSet
          }
          val ruleInstanceBlocks = List(rulesToSpacingDefiningBlocks.get(ruleInstance).getOrElse(List()))
          if (needWrapInReadAction) {
            wrapInReadAction {
              res.put(ruleInstance, ScalaFormattingRuleMatcher.deduceWraps(checkedEntries.toList, ruleInstanceBlocks))
            }
          } else {
            res.put(ruleInstance, ScalaFormattingRuleMatcher.deduceWraps(checkedEntries.toList, ruleInstanceBlocks))
          }
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
  def deduceIndentSettings(rulesToSettings: Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]): List[FormattingSettings] = {
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
      var curSettings = ruleToIndentSettings.get(ruleToIndentSettings.keys.toList.head).get
      for (rule <- ruleToIndentSettings.keys.toList.tail) {
        for (setting <- ruleToIndentSettings.get(rule).get) {
          var newCurrentSettings = List[FormattingSettings]()
          for (currentSetting <- curSettings) {
            currentSetting.unify(setting, this) match {
              case None =>
              case Some(newSetting) => newCurrentSettings = newSetting :: newCurrentSettings
            }
          }
          curSettings = newCurrentSettings
        }
      }
      curSettings
    }
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

  /**
   * Returns all relations different from normal/continuation indent relations active for given rule instances.
   * @param ruleInstances
   * @return
   */
  private def getNonIndentRelations(ruleInstances: Iterable[ScalaFormattingRuleInstance]): Set[RuleRelation] = {
    val rules = ruleInstances.map(_.rule).toSet
    rules.map(_.relations.map(_._1).filter(!_.isIndentTypeRelation)).fold(Set())((acc, cur) => acc.union(cur))
  }

  /**
   *
   * @param rulesToEntries
   * @param relations
   */
  def processAlignments(rulesToEntries: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                        relations: Set[RuleRelation]) = {
    def collectBlocks(topMatchToBlocks: mutable.Map[RuleMatch, Set[ScalaBlock]],
                      ruleInstance: ScalaFormattingRuleInstance) = {
      for (block <- rulesToSpacingDefiningBlocks.getOrElse(ruleInstance, List())) {
        getTopMatch(block, ruleInstance) match {
          case Some(topMatch) =>
//            println("added block " + block.getNode.getText)
            topMatchToBlocks.put(topMatch,  topMatchToBlocks.getOrElse(topMatch, Set()) + block)
          case None =>
            throw new IllegalStateException("the top match is missing for block" + block +
                    "in rule instance " + ruleInstance )
        }
      }
    }

    //assume a rule can be aligned by not more then one relation
    val singleAlignedRules = mutable.Set(rulesToEntries.keySet.map(_.rule).toSeq:_*)
    val ruleClusters = mutable.ArrayBuffer[List[ScalaFormattingRule]]()
    for (relation <- relations) {
      //if this relation connects some rules in terms of alignment, remember them as a cluster and remove from single aligned rules
      val alignedRules = relation.getAlignedRules
      if (alignedRules.size > 0) {
        ruleClusters += alignedRules
        for (rule <- alignedRules) {
          //assert(singleAlignedRules.contains(rule)) //this is used to ensure that every rule is aligned by not more than 1 relation
          singleAlignedRules.remove(rule)
        }
      }
    }

    //now rule alignment clusters are built, build block alignment clusters
    //first, construct a mapping (rule -> ruleInstance)
    val rulesToRuleInstances = mutable.Map[ScalaFormattingRule, List[ScalaFormattingRuleInstance]]()
    for (ruleInstance <- rulesToEntries.keys) {
      val rule = ruleInstance.rule
      rulesToRuleInstances.put(rule, ruleInstance :: rulesToRuleInstances.getOrElse(rule, List()))
    }

    val blockAlignmentClusters = mutable.ArrayBuffer[Set[ScalaBlock]]()

    //for every single-aligned instance just traverse blocks by top matches and put them into clusters
    for (rule <- singleAlignedRules if rule.isBlockRule) {
      for (ruleInstance <- rulesToRuleInstances.getOrElse(rule, List())) {
        val topMatchToBlocks = mutable.Map[RuleMatch, Set[ScalaBlock]]()
        collectBlocks(topMatchToBlocks, ruleInstance)
        for (blockCluster <- topMatchToBlocks.values) {
          blockAlignmentClusters += blockCluster
        }
      }
    }

    //for every same-aligned rule cluster put blocks in same top match together even when rules or instances differ
    for (ruleAlignmentCluster <- ruleClusters) {
      val topMatchToBlocks = mutable.Map[RuleMatch, Set[ScalaBlock]]()
      for (rule <- ruleAlignmentCluster) {
        assert(rule.isBlockRule)
        for (ruleInstance <- rulesToRuleInstances.getOrElse(rule, List())) {
          collectBlocks(topMatchToBlocks, ruleInstance)
          val ruleEntries = rulesToEntries.getOrElse(ruleInstance, List())
          val newRuleEntries = mutable.Set(ruleEntries:_*)
          //add wrap possibility where necessary
          for (entry <- ruleEntries) {
            newRuleEntries += entry.setAlignment(true)
          }
          rulesToEntries.put(ruleInstance, newRuleEntries.toList)
        }
      }
      for (blockCluster <- topMatchToBlocks.values) {
        if (!blockAlignmentClusters.contains(blockCluster))
        blockAlignmentClusters += blockCluster
      }
    }

    val entriesAsList = rulesToEntries.values.flatten.toList

    val processedEntries = ScalaFormattingRuleMatcher.deduceAlignments(entriesAsList, blockAlignmentClusters)
//    val processedEntries = ScalaFormattingRuleMatcher.deduceAlignments(rulesToEntries, blockAlignmentClusters)

    rulesToEntries.clear()

    for (entry <- processedEntries) {
      rulesToEntries.put(entry.rule, entry::rulesToEntries.getOrElse(entry.rule, List()))
    }
  }

  def checkLineSpacingsConsistent(
    maps: List[mutable.Map[ScalaFormattingRuleInstance, scala.List[ScalaBlockFormatterEntry]]],
    blocksByStartPos: mutable.Map[Integer, List[ScalaBlock]]
  ): List[mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]] = {
    var res: List[mutable.Map[ScalaFormattingRuleInstance, scala.List[ScalaBlockFormatterEntry]]] = maps
    for ((startPos, blocks) <- blocksByStartPos if blocks.size > 1) {
      //there was more then one block, probably should split currently present maps on it
      val buf = List[mutable.Map[ScalaFormattingRuleInstance, scala.List[ScalaBlockFormatterEntry]]]()
      for (currentMap <- res) {
        //traverse all settings maps, split maps that have conflicts
        //maps ruleInstance to pair (possibly conflicted entries, not conflicted entries)
        val conflictMap = currentMap.map(mapEntry => {
            val (ruleInstance, entries) = mapEntry
          (ruleInstance, entries.partition(_.instances.exists(blocks.contains)))
          })
        for ((instance, (conflictEntries, freeEntries)) <- conflictMap if conflictEntries.nonEmpty) {

        }
      }
      res = buf
    }
    false
    null
  }

  def deriveInitialSettings(failLogger: Option[SettingDeductionFailLogger] = None,
                            needWrapInReadAction: Boolean,
                            processIndentsOnly: Boolean):
  mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]] = {
    val rulesToEntries = mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]()
    val processedRules = mutable.Set[ScalaFormattingRuleInstance]()

    for ((topRule, childRules) <- topRulesToSubRules) {
      val possibleSettings = mutable.Map[ScalaFormattingRuleInstance, List[(ScalaBlock, List[ScalaBlockFormatterEntry])]]()
      var rulesToUnifyBlocksFor = List[ScalaFormattingRuleInstance]()
      for (child <- childRules if !processedRules.contains(child)) {
        var possibleRuleSettings = List[(ScalaBlock, List[ScalaBlockFormatterEntry])]()
        for (matchedBlock <- rulesToSpacingDefiningBlocks.getOrElse(child, List())) {
          if (needWrapInReadAction) {
            wrapInReadAction {
              possibleRuleSettings =
                      (matchedBlock, ScalaFormattingRuleMatcher.getPossibleSpacingSettings(matchedBlock, child)) ::
                              possibleRuleSettings
            }
          } else {
            possibleRuleSettings =
                    (matchedBlock, ScalaFormattingRuleMatcher.getPossibleSpacingSettings(matchedBlock, child)) ::
                            possibleRuleSettings
          }
        }
        possibleSettings.put(child, possibleRuleSettings)
        //mark the rule so that we don't try to deduce entries for it multiple times
        processedRules.add(child)
        //add the rule into a block settings unification queue (notice that since the rule has just been marked as processed, block settings unification will be performed only once)
        rulesToUnifyBlocksFor = child :: rulesToUnifyBlocksFor
      }
      //make entries for blocks within single rule consistent in terms of spacings, indents and wraps
      val singleRulesProcessed =
        processSingleRules(rulesToUnifyBlocksFor, possibleSettings, failLogger, needWrapInReadAction, processIndentsOnly)
      for (entry <- singleRulesProcessed) {
        rulesToEntries += entry
      }
    }

    rulesToEntries
  }

  def processRelations(rulesToEntries: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                       doProcessNonIndentRelations: Boolean = true,
                       failLogger: Option[SettingDeductionFailLogger] = None): FormattingSettingsTree#LayeredTraversal = {
    val baseMap = Map(rulesToEntries.toSeq:_*)

    val root = FormattingSettingsTree(rulesToEntries.keys.toSeq, new FormattingSettings(None, None, baseMap))

    val startingLayer: FormattingSettingsTree#LayeredTraversal = root.getRootTraversal

    val indentsStartLayer = if (doProcessNonIndentRelations) {
      processNonIndentRelations(startingLayer, rulesToEntries, failLogger)
    } else {
      startingLayer
    }

    processIndentRelations(indentsStartLayer)

  }


  def processIndentRelations(startingLayer: FormattingSettingsTree#LayeredTraversal,
                             failLogger: Option[SettingDeductionFailLogger] = None): FormattingSettingsTree#LayeredTraversal = {

    val currentLayer = IndentTypeRelation.continuationIndentRelation.filter(startingLayer)

    IndentTypeRelation.normalIndentRelation.filter(currentLayer)
  }

  def processNonIndentRelations(startingLayer: FormattingSettingsTree#LayeredTraversal,
                       rulesToEntries: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                       failLogger: Option[SettingDeductionFailLogger] = None):
  FormattingSettingsTree#LayeredTraversal = {
    val relations = getNonIndentRelations(rulesToEntries.keySet)

    processAlignments(rulesToEntries, relations)

    //rule relations processing goes here

    //var relationSettings = List(rulesToEntries)

    var currentLayer: FormattingSettingsTree#LayeredTraversal = startingLayer

    for (relation <- relations) {
      currentLayer = relation.filter(currentLayer)
    }

    currentLayer
  }

  //On input: unprocessed data on spacings before all blocks
  //On output: list of possible formatting settings for all rules or identification of a conflict
  /**
   *
   * @return
   */
  def deriveSettings(project: Project,
                     failLogger: Option[SettingDeductionFailLogger] = None,
                     needWrapInReadAction: Boolean = false,
                     processNonIndentRelations: Boolean = true): FormattingSettings = {

    val rulesToEntries = deriveInitialSettings(failLogger, needWrapInReadAction, !processNonIndentRelations)

    val relationPossibilitiesLayer = processRelations(rulesToEntries, processNonIndentRelations)

//    relationPossibilitiesLayer.traverse(
//      (node, nodeEntry) => {
//        node.formattingSettings match {
//          case Some(settings) =>
//            val deducedSettings = deduceIndentSettings(settings.instances)
//            node.splitIndents(deducedSettings)
//          case _ =>
//        }
//      }
//    )

    val finalLayer = relationPossibilitiesLayer.descend

    currentSettings = resolveMultiplePossibleSettings(finalLayer, isInteractive = false, project)
//    currentSettings = deduceIndentSettings(Map(rulesToEntries.toList:_*)).head
    currentSettings
  }

  def loadExampleBase = {
    val exampleBaseFile = new File("testBase")
    val document = (new SAXBuilder).build(exampleBaseFile)
    ExampleBase.load(document.getRootElement)
  }

  def resolveMultiplePossibleSettings(finalLayer: FormattingSettingsTree#LayeredTraversal,
                                      isInteractive: Boolean,
                                      project: Project): FormattingSettings = {

    def chooseByExample(examples: Map[ScalaFormattingRule, scala.List[ScalaBlock]],
                        ruleInstance: ScalaFormattingRuleInstance,
                        variants: List[ScalaBlockFormatterEntry]): ScalaBlockFormatterEntry = {
      variants.head
    }

    def resolveLastLayerSettings(settings: FormattingSettings, examples: Map[ScalaFormattingRule, scala.List[ScalaBlock]]): FormattingSettings = {
      val newInstances = mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]()
      for (ruleInstance <- settings.instances.keys) {
        settings.instances.get(ruleInstance) match {
          case Some(entries) if entries.length > 1 =>
            newInstances.put(ruleInstance, List(chooseByExample(examples, ruleInstance, entries)))
          case other =>
            newInstances.put(ruleInstance, other.getOrElse(List()))
        }
      }
      new FormattingSettings(settings.normalIndentSize, settings.continuationIndentSize, Map(newInstances.toSeq:_*))
    }

    def chooseNoExamples(ruleInstance: ScalaFormattingRuleInstance,
                            children: List[(FormattingSettingsTree, ScalaBlockFormatterEntry)]) = {
      //TODO: implement me more thoughfully
      children.head._1
    }

    def chooseFromExamples(examples: List[ScalaBlock],
                           children: List[(FormattingSettingsTree, ScalaBlockFormatterEntry)]) = {
      //TODO: implement me with user communication and other stuff
      children.head._1
    }

    def chooseFromIndentExamples(children: List[(FormattingSettingsTree, ScalaBlockFormatterEntry)]) = {
      //TODO: implement me with user communication and other stuff
      children.head._1
    }

    @tailrec
    def bfsSettingsTree(rulesToBlocksMap: Map[ScalaFormattingRule, scala.List[ScalaBlock]],
                               node: FormattingSettingsTree): FormattingSettingsTree = {
      node.childrenWithEntries match {
        case Some(children) if node.isIndentSplit =>
            bfsSettingsTree(rulesToBlocksMap, chooseFromIndentExamples(children))
        case Some(children) if !node.isIndentSplit =>
          val ruleInstance = node.conflictRule.get
          val examplesOpt = rulesToBlocksMap.get(ruleInstance.root)
          examplesOpt match {
            case Some(examples) =>
              bfsSettingsTree(rulesToBlocksMap, chooseFromExamples(examples, children))
            case None =>
              bfsSettingsTree(rulesToBlocksMap, chooseNoExamples(ruleInstance, children))
          }
        case None =>
          node
      }
    }

    if (isInteractive) {
      exampleBase match {
        case None => exampleBase = Some(loadExampleBase)
        case _ =>
      }
      val ruleToBlocksMap = exampleBase.get.getRuleToBlocksMap(project)
      val finalNode = bfsSettingsTree(ruleToBlocksMap, finalLayer.getRoot)
      resolveLastLayerSettings(finalNode.formattingSettings.get, ruleToBlocksMap)
    } else {
      finalLayer.traverse(
        (node, nodeEntry) => {
          node.formattingSettings match {
            case Some(settings) => return settings
            case _ =>
          }
        }
      )
      null
    }
  }

  def convertSettings(settings: ScalaCodeStyleSettings, commonSettings: CommonCodeStyleSettings): FormattingSettings = {
    val ruleInstanceToEntry = mutable.Map[ScalaFormattingRuleInstance, ScalaBlockFormatterEntry]()
    def modifyEntry(instance: ScalaFormattingRuleInstance, modifier: (ScalaBlockFormatterEntry => ScalaBlockFormatterEntry)) = {
      ruleInstanceToEntry.put(instance, modifier(ruleInstanceToEntry.getOrElse(instance, ScalaBlockFormatterEntry(instance))))
    }
//    def setMinLineFeeds(count: Int)(entry: ScalaBlockFormatterEntry) = entry.setSpacing(entry.spacing.setMinLineFeeds(count))

    def setLineFeeds(count: Int)(entry: ScalaBlockFormatterEntry) = entry.setSpacing(entry.spacing.setLineFeeds(count))

    def setAlignment(needAlignment: Boolean)(entry: ScalaBlockFormatterEntry) = entry.setAlignment(needAlignment)

    //for every settings, build a dummy match and then try to unify them all (i.e. derive settings)
    val ALIGN_IF_ELSE_IF_instance = ruleInstancesByTags.get(ALIGN_IF_ELSE_TAG_IF_WORD)
    val ALIGN_IF_ELSE_ELSE_instance = ruleInstancesByTags.get(ALIGN_IF_ELSE_TAG_ELSE_WORD)
    (ALIGN_IF_ELSE_IF_instance, ALIGN_IF_ELSE_ELSE_instance) match {
      case (Some(ifInstance), Some(elseInstance)) =>
        modifyEntry(ifInstance, _.setAlignment(settings.ALIGN_IF_ELSE))
        modifyEntry(elseInstance, _.setAlignment(settings.ALIGN_IF_ELSE))
      case _ =>
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
    val idChainRuleInstance = ruleInstancesByTags.get(idChainTag)
    if (idChainRuleInstance.isDefined) {
      modifyEntry(idChainRuleInstance.get, setAlignment(commonSettings.ALIGN_MULTILINE_CHAINED_METHODS))
    }

    val parametersInstance = ruleInstancesByTags.get(parametersAlignmentTag)
    if (parametersInstance.isDefined) {
      modifyEntry(parametersInstance.get, setAlignment(commonSettings.ALIGN_MULTILINE_PARAMETERS))
    }
    commonSettings.ALIGN_MULTILINE_PARAMETERS



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

  def createDefaultMatcher(project: Option[Project] = None): ScalaFormattingRuleMatcher =
    new ScalaFormattingRuleMatcher(topRulesByIds, project)

  def createDefaultMatcher(project: Project): ScalaFormattingRuleMatcher = createDefaultMatcher(Some(project))

  def topRulesByIds: Map[String, ScalaFormattingRule] =
  //first, construct default rules
    Map[String, ScalaFormattingRule](
//      rule.whileDefault.id -> rule.whileDefault,
//      rule.doDefault.id -> rule.doDefault,
      rule.ifDefault.id -> rule.ifDefault,
//      rule.caseClausesComposite.id -> rule.caseClausesComposite,
//      rule.tryDefault.id -> rule.tryDefault,
//      rule.matchRule.id -> rule.matchRule,
      rule.idChainDefault.id -> rule.idChainDefault
//      rule.parametersDefault.id -> rule.parametersDefault,
//      rule.typeParametersList.id -> rule.typeParametersList,
//      rule.forDefault.id -> rule.forDefault
//      rule.importChainDefault.id -> rule.importChainDefault
    )
  //Map[String, ScalaFormattingRule](rule.caseClausesComposite.id -> rule.caseClausesComposite)

  private def runMatcher(rootBlock: ScalaBlock) = {
    val matcher = new ScalaFormattingRuleMatcher(topRulesByIds, Some(rootBlock.getNode.getPsi.getProject))
    matcher.matchBlockTree(rootBlock)
    matcher
  }

  def testExtraction(rootBlock: ScalaBlock, rulesNames: Array[String], project: Project): String = {
    val matcher = runMatcher(rootBlock)
    matcher.deriveSettings(project)
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

  val wrapTypes = List[WrapType](WrapType.ALWAYS, WrapType.CHOP_DOWN_IF_LONG, /*WrapType.NONE,*/ WrapType.NORMAL)

  /**
   * Given a list of formatter entries and list of groups of blocks that should have the same wrapping settings,
   * construct a list of formatter entties
   * @param entries
   * @param sameFormattingBlocks
   * @return
   */
  def deduceWraps(entries: List[ScalaBlockFormatterEntry], sameFormattingBlocks: List[List[ScalaBlock]]): List[ScalaBlockFormatterEntry] = {
    var res = List[ScalaBlockFormatterEntry]()
    for (entry <- entries) {
      if (entry.wrap.wrapDefined) {
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

  def wrapTypeApplicable(blocks: List[ScalaBlock], wrapType: WrapType): Boolean = {
    wrapType match {
      case WrapType.ALWAYS =>
        blocks.forall(_.isOnNewLine)
      case WrapType.CHOP_DOWN_IF_LONG =>
        blocks.forall(!_.wouldCrossRightMargin) || blocks.forall(_.isOnNewLine)
      case WrapType.NORMAL =>
        blocks.forall((block) => !block.wouldCrossRightMargin || block.isOnNewLine)
      case WrapType.NONE =>
        blocks.forall((block) => !block.wouldCrossRightMargin || !block.isOnNewLine)
    }
  }

  /**
   *
   * @param entries
   * @param sameFormattingBlocks
   * @return
   */
  def deduceAlignments(entries: List[ScalaBlockFormatterEntry], sameFormattingBlocks: Iterable[Set[ScalaBlock]]): List[ScalaBlockFormatterEntry] = {
    var res = List[ScalaBlockFormatterEntry]()
    /**
     * Splits a list of blocks into a list of collections such that all blocks in each collection should have the same formatting settings.
     * @param blocks
     * @param acc
     * @return
     */
    @tailrec
    def splitBlocks(blocks: List[ScalaBlock], acc: List[Set[ScalaBlock]]): List[Set[ScalaBlock]] = {
      if (blocks.isEmpty) acc
      else {
        val buf = sameFormattingBlocks.find(_.contains(blocks.head)).getOrElse(List[ScalaBlock](blocks.head)).toSet
        splitBlocks(blocks.tail.filterNot(buf.contains(_)), buf :: acc)
      }
    }
    for (entry <- entries) {
      if (entry.alignment.needAlignment) {
        val alignmentGroups = splitBlocks(entry.instances, List[Set[ScalaBlock]]())
        //check that picked alignment group has the same alignment
        val testVal = alignmentGroups.forall(
          alignmentGroup => {
            val lineToOffsetMap = mutable.Map[Integer, Integer]()
            for (block <- alignmentGroup) {
              val (lineNum, offset) = (block.getLineNumber, block.getInLineOffset)
//              println("line = " + lineNum + " offset = " + offset)
              val currentMin: Integer = lineToOffsetMap.getOrElse(lineNum, Integer.MAX_VALUE)
              if (offset <= currentMin) lineToOffsetMap.put(lineNum, offset)
            }
            lineToOffsetMap.values.toSet.size == 1
          }
        )
        if (testVal) {
          res = entry :: res
        }
      } else {
        res = entry :: res
      }
    }
    res
  }

  /**
   * Returns a list of spacing and indent settings that could produce formatting for given block in text.
   * @param block block to deduce possible formatting settings for
   * @param ruleInstance
   * @return list of tuples of Spacing, Indent, Wrap and Alignment settings. Every entry is a possible formattign setting.
   */
  def getPossibleSpacingSettings(block: ScalaBlock, ruleInstance: ScalaFormattingRuleInstance): List[ScalaBlockFormatterEntry] = {
    if (block.getNode.getTreePrev == null && block.myParentBlock != null && block.getLineNumber == block.myParentBlock.getLineNumber) {
      //by default whitespace belongs to the parent, so we act as if we had "" spacing
      List(ScalaBlockFormatterEntry(SpacingInfo(""), Some(IndentInfo(0, true)), block, ruleInstance, originatingFromNoSpaceChild = true, false))
      //Zero indent info is a hack, there should be None indent info and a check for consistency in the end of settings
      //deduction. However, it's not quite trivial and the hack happens to work.
    } else {
      var res = List[ScalaBlockFormatterEntry]()
      //whitespace belongs to this block, process it as needed
      val whitespace = block.getInitialWhiteSpace
      val hasNewline = whitespace.contains("\n")
      //cases when whitespace is produced by spacing
      if (hasNewline) {
        //the spacing is a bunch of newlines or wrap
        val spacing = whitespace.substring(0, whitespace.lastIndexOf("\n") + 1)
        val directIndent = block.getIndentFromDirectParent
        val couldBeWrap = spacing.count(_ == '\n') == 1
        var indentDefinable = false
        if (directIndent >= 0) {
          indentDefinable = true
          res = ScalaBlockFormatterEntry(SpacingInfo(spacing), IndentInfo(block.getIndentFromDirectParent,
            indentRelativeToDirectParent = true), block, ruleInstance) :: res
          if (couldBeWrap) {
            res = ScalaBlockFormatterEntry(SpacingInfo(spacing), IndentInfo(block.getIndentFromDirectParent,
              indentRelativeToDirectParent = true), block, ruleInstance, wrap = true) :: res
          }
        }
        block.getIndentFromNewlineAncestor match {
          case Some(indent) if indent >= 0 =>
            indentDefinable = true
            res = ScalaBlockFormatterEntry(SpacingInfo(spacing), IndentInfo(indent,
              indentRelativeToDirectParent = false), block, ruleInstance) :: res
            if (couldBeWrap) {
              res = ScalaBlockFormatterEntry(SpacingInfo(spacing), IndentInfo(indent,
                indentRelativeToDirectParent = false), block, ruleInstance, wrap = true) :: res
            }
          case _ =>
        }
        if (!indentDefinable) {
          res = ScalaBlockFormatterEntry(SpacingInfo(spacing), block, ruleInstance) :: res
          if (couldBeWrap) {
            res = ScalaBlockFormatterEntry(SpacingInfo(spacing), None, block, ruleInstance, false, wrap = true) :: res
          }
        }



        //        if (whitespace.endsWith("\n")) {
        //          //maybe it's just newline spacing

//        res = ScalaBlockFormatterEntry(SpacingInfo(whitespace), block, ruleInstance) :: res
        //        if (spacing.count(_ == '\n') == 1) {
        //          //it's a simple newline, could be wrap
        //          res = ScalaBlockFormatterEntry(SpacingInfo(), block, ruleInstance)
        //        }
        //        }
      } else {
        //plain spacing
        res = ScalaBlockFormatterEntry(SpacingInfo(whitespace), block, ruleInstance) :: res
      }
      res
    }
  }
}