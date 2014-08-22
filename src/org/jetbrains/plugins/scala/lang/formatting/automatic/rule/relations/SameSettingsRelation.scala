package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule.relations

import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation.RelationParticipantId
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{ScalaBlockFormatterEntry, ScalaFormattingRuleInstance}
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.tree.FormattingSettingsTree
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType

/**
 * Relation that handles conditions of type "blocks that are matched by the rule should have the same settings". By
 * default handles cases where all the settings should be the same and is customizable to handle cases when only selected
 * settings should be the same as well as cases with additional logic added.
 */
class SameSettingsRelation(val id: String) extends RuleRelation {

  protected def indentType: Option[IndentType.IndentType] = None

  protected var sameWrapRules: List[ScalaFormattingRule] = List()
  protected var sameSpacingRules: List[ScalaFormattingRule] = List()
  protected var sameIndentRules: List[ScalaFormattingRule] = List()
  protected var sameAlignmentRules: List[ScalaFormattingRule] = List()

  override def addRule(rule: ScalaFormattingRule, participantIds: RelationParticipantId*) {
    registerRule(rule)
    if (!participantIds.contains(SameSettingsRelation.noSpacingId)) {
      sameSpacingRules = rule :: sameSpacingRules
    }
    if (!participantIds.contains(SameSettingsRelation.noIndentId)) {
      sameIndentRules = rule :: sameIndentRules
    }
    if (!participantIds.contains(SameSettingsRelation.noWrapId)) {
      sameWrapRules = rule :: sameWrapRules
    }
    if (!participantIds.contains(SameSettingsRelation.noAlignmentId)) {
      sameAlignmentRules = rule :: sameAlignmentRules
    }
  }

  protected def processSameWrapRules(res: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                                     ruleInstance: ScalaFormattingRuleInstance,
                                     entry: ScalaBlockFormatterEntry,
                                     entries: mutable.ListBuffer[ScalaBlockFormatterEntry],
                                     entriesAdded: mutable.Set[ScalaBlockFormatterEntry]) {
    //since wraps are set only when they are needed, also process cases when wraps are not needed, but could still be set
    if (sameWrapRules.contains(ruleInstance.rule)) {
      val sameWrapMap = res.filter(mapEntry => sameWrapRules.contains(mapEntry._1.rule))
      for ((otherInstance, otherEntries) <- sameWrapMap if otherInstance!=ruleInstance) {
        val otherSet = otherEntries.map(_.reduceWrapTo(entry)).filter(_.isDefined).map(_.get).toSet
        for (altEntry <- otherEntries.map(entry.reduceWrapTo).filter(_.isDefined).map(_.get)) {
          if (!entriesAdded.contains(altEntry)) {
            entriesAdded.add(altEntry)
            entries += altEntry
          }
        }
        res.put(otherInstance, otherSet.filter(otherEntry => entry.wrap == otherEntry.wrap).toList)
      }
    }
  }

  protected def processSameSpacingRules(res: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                                        ruleInstance: ScalaFormattingRuleInstance,
                                        entry: ScalaBlockFormatterEntry,
                                        entries: mutable.ListBuffer[ScalaBlockFormatterEntry],
                                        entriesAdded: mutable.Set[ScalaBlockFormatterEntry]) {
    //spacing filtering is only concerned with removing formatter entries that are not consistent with requirements
    if (sameSpacingRules.contains(ruleInstance.rule)) {
      val sameSpacingMap = res.filter(mapEntry => sameSpacingRules.contains(mapEntry._1.rule))
      for ((otherInstance, otherEntries) <- sameSpacingMap if otherInstance != ruleInstance) {
        //TODO: for now, different counts of newlines are not supported
        res.put(otherInstance, otherEntries.filter(otherEntry => entry.spacing == otherEntry.spacing))
      }
    }
  }

  protected def processSameIndentRules(res: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                                       ruleInstance: ScalaFormattingRuleInstance,
                                       entry: ScalaBlockFormatterEntry,
                                       entries: mutable.ListBuffer[ScalaBlockFormatterEntry],
                                       entriesAdded: mutable.Set[ScalaBlockFormatterEntry],
                                       indentType: Option[IndentType.IndentType]) {
    if (sameIndentRules.contains(ruleInstance.rule)) {
      val sameIndentMap = res.filter(mapEntry => sameIndentRules.contains(mapEntry._1.rule))
      for ((otherInstance, otherEntries) <- sameIndentMap if otherInstance != ruleInstance) {
        val otherSet = otherEntries.map(_.reduceIndentTo(entry)).filter(_.isDefined).map(_.get).toSet
        for (altEntry <- otherEntries.map(entry.reduceIndentTo).filter(_.isDefined).map(_.get)) {
          if (!entriesAdded.contains(altEntry)) {
            entriesAdded.add(altEntry)
            entries += altEntry
          }
        }
        res.put(otherInstance, otherSet.filter(otherEntry => entry.indentInfo == otherEntry.indentInfo).
                map(entry => indentType match {
          case Some(iType) => entry.setIndentType(iType)
          case None => entry
        }).toList)
      }
      indentType match {
        case Some(iType) => res.put(ruleInstance, res.getOrElse(ruleInstance, List()).map(_.setIndentType(iType)))
        case _ =>
      }
    }
  }

  protected def processSameAlignmentRules(res: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                                          ruleInstance: ScalaFormattingRuleInstance,
                                          entry: ScalaBlockFormatterEntry,
                                          entries: mutable.ListBuffer[ScalaBlockFormatterEntry],
                                          entriesAdded: mutable.Set[ScalaBlockFormatterEntry]) {
    if (sameAlignmentRules.contains(ruleInstance.rule)) {
      val sameAlignmentMap = res.filter(mapEntry => sameAlignmentRules.contains(mapEntry._1.rule))
      for ((otherInstance, otherEntries) <- sameAlignmentMap if otherInstance != ruleInstance) {
        res.put(otherInstance, otherEntries.filter(otherEntry => entry.alignment == otherEntry.alignment))
      }
    }
  }

  /**
   * Given a certain entry selection for given rule instance and a map of instances to possible entries,
   * constructs a new map of instances to possible entries that is consistent with the relation.
   * @param ruleInstance
   * @param entry
   * @param map
   * @return
   */
  protected def filterMap(ruleInstance: ScalaFormattingRuleInstance,
                entry: ScalaBlockFormatterEntry,
                map: Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                entries: mutable.ListBuffer[ScalaBlockFormatterEntry],
                entriesAdded: mutable.Set[ScalaBlockFormatterEntry],
                indentType: Option[IndentType.IndentType] = indentType):
  Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]] = {
    val res = mutable.Map(map.toSeq:_*)

//    if (res.exists(value => value._2.isEmpty)) {
//      return mutable.Map()
//    }

    res.put(ruleInstance, List(entry))

    processSameWrapRules(res, ruleInstance, entry, entries, entriesAdded)

    processSameSpacingRules(res, ruleInstance, entry, entries, entriesAdded)

    processSameIndentRules(res, ruleInstance, entry, entries, entriesAdded, indentType)

    processSameAlignmentRules(res, ruleInstance, entry, entries, entriesAdded)

    Map(res.toSeq:_*)
  }

//  /**
//   * Produce all maps that correspond to given entries. If necessary, use acceptable modifications of input entries.
//   * @param entries
//   * @return
//   */
//  def processEntries(entries: List[ScalaBlockFormatterEntry],
//                     caseMap: Map[ScalaFormattingRuleInstance, scala.List[ScalaBlockFormatterEntry]]) = {
//    val testedEntries = mutable.Set(entries:_*)
//    //first, build tested set by adding entries created by weakening constraints on input entries
//    for (entry <- entries) {
//      //for every entry build all possible entries obtainable from weakening constraints to comply with relation
//      val sameSpacingMap = caseMap.filter(mapEntry => sameSpacingRules.contains(mapEntry._1.rule))
//      for ((sameInstance, sameEntries) <- sameSpacingMap) {
//
//      }
//    }
//  }

//  protected def performSplit(currentNode: FormattingSettingsTree,
//                             ruleInstance: ScalaFormattingRuleInstance,
//                             childrenMaps: mutable.ListBuffer[
//                                       Map[ScalaFormattingRuleInstance, scala.List[ScalaBlockFormatterEntry]]
//                                     ]) = currentNode.split(ruleInstance, childrenMaps)

  protected def filterSameSettings(inputLayer: FormattingSettingsTree#LayeredTraversal):
  FormattingSettingsTree#LayeredTraversal = {
    //iterate over maps, split every if needed
    val rules = getRules.toList

    var currentLayer = inputLayer

    val ruleInstances = currentLayer.getRoot.ruleInstances

    for (ruleInstance <- ruleInstances if rules.contains(ruleInstance.rule)) {
      //go down one layer resolving possible settings for selected ruleInstance
      currentLayer.traverse(
        (currentNode, currentEntry) => {
          currentNode.formattingSettings match {
            case Some(formattingSettings) =>
              val caseMap = formattingSettings.instances
              currentNode.getEntriesForRuleInstance(ruleInstance) match {
                case Some(currentEntries) =>
                  val childrenMaps = mutable.ListBuffer[Map[ScalaFormattingRuleInstance, scala.List[ScalaBlockFormatterEntry]]]()
                  val entries = mutable.ListBuffer(currentEntries:_*)
                  val entriesAdded = mutable.Set[ScalaBlockFormatterEntry]()
                  while (entries.nonEmpty) {
                    val ruleEntry = entries.head
                    val cutMap = filterMap(ruleInstance, ruleEntry, caseMap, entries, entriesAdded)
                    childrenMaps += cutMap
                    entries.remove(0)
                  }
//                  performSplit(currentNode, ruleInstance, childrenMaps)
                  val settings = currentNode.formattingSettings
                  val normalIndent = settings.map(_.normalIndentSize).flatten
                  val continuationIndent = settings.map(_.continuationIndentSize).flatten
                  currentNode.split(ruleInstance, childrenMaps, normalIndent, continuationIndent)
                case _ =>
              }
            case _ =>
          }
        }
      )
      currentLayer = currentLayer.descend
    }

    currentLayer
  }


//  override def filter(rulesToEntries: List[mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]]): List[mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]] = {
//    filterSameSettings(rulesToEntries)
//  }

  override def filter(startingLayer: FormattingSettingsTree#LayeredTraversal): FormattingSettingsTree#LayeredTraversal = {
    filterSameSettings(startingLayer)
  }

  def isAlignedByThisRelation(rule: ScalaFormattingRule): Boolean = sameAlignmentRules.contains(rule)

  override def getAlignedRules = sameAlignmentRules
}

object SameSettingsRelation {
  val noSpacingId = "NO SPACING"
  val noIndentId = "NO INDENT"
  val noWrapId = "NO WRAP"
  val noAlignmentId = "NO ALIGNMENT"
  val serializationId = "SAME_SETTINGS_RELATION"
}
