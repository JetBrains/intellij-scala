package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{ScalaBlockFormatterEntry, ScalaFormattingRuleInstance}

/**
* Created by Roman.Shein on 24.06.2014.
*/
class FormattingSettingsTree private (private var childrenAgg: Option[(ScalaFormattingRuleInstance, List[(FormattingSettingsTree, ScalaBlockFormatterEntry)])] = None,
                                      val parent: Option[FormattingSettingsTree],
                                      var formattingSettings: Option[FormattingSettings] = None,
                                      val ruleInstances: Seq[ScalaFormattingRuleInstance]) {

  def getEntriesForRuleInstance(ruleInstance: ScalaFormattingRuleInstance): Option[List[ScalaBlockFormatterEntry]] = {
    formattingSettings match {
      case Some(settings) => settings.instances.get(ruleInstance)
      case _ => None
    }
  }

  def getRootTraversal = new LayeredTraversal(List((this, None)))

  class LayeredTraversal (val currentLayer: List[(FormattingSettingsTree, Option[ScalaBlockFormatterEntry])]) {
    def traverse(fun: ((FormattingSettingsTree, Option[ScalaBlockFormatterEntry]) => Unit)) = {
      for ((node, entry) <- currentLayer) {
        fun(node, entry)
      }
    }

    def descend: LayeredTraversal = {
      var nextLayer = List[(FormattingSettingsTree, Option[ScalaBlockFormatterEntry])]()
      for ((node, entry) <- currentLayer) {
        node.childrenWithEntries match {
          case Some(children) => nextLayer = nextLayer ::: children.map(arg => (arg._1, Some(arg._2)))
          case _ => nextLayer = nextLayer ::: List((node, None))
        }
      }
      new LayeredTraversal(nextLayer)
    }

    def getRoot = FormattingSettingsTree.this
  }

  def conflictRule = childrenAgg.map(_._1)

  def childrenWithEntries = childrenAgg.map(_._2)

  def split(ruleInstance: ScalaFormattingRuleInstance, childrenMaps: Seq[Map[ScalaFormattingRuleInstance, scala.List[ScalaBlockFormatterEntry]]]) {
    splitSettings(ruleInstance, childrenMaps.map(childMap => new FormattingSettings(None, None, childMap)))

//    childrenAgg match {
//      case Some((rule, children)) =>
//        throw new IllegalStateException("Split operation failed: the node already has children.")
//      case _ =>
//        formattingSettings = None
//        var childrenList = List[(FormattingSettingsTree, ScalaBlockFormatterEntry)]()
//        for (childMap <- childrenMaps) {
//          val fixedEntry = childMap.get(ruleInstance).get
//          assert(fixedEntry.size == 1)
//          childrenList = (new FormattingSettingsTree(None, Some(this), None, this.ruleInstances), fixedEntry.head) :: childrenList
//        }
//        childrenAgg = Some((ruleInstance, childrenList))
//    }
  }

  def splitIndents(settings: Seq[FormattingSettings]) {
    childrenAgg match {
      case Some((rule, children)) =>
        throw new IllegalStateException("Split operation failed: the node already has children.")
      case _ =>
        formattingSettings = None
        val childrenList = settings.map(setting => (new FormattingSettingsTree(None, Some(this), Some(setting), null), null: ScalaBlockFormatterEntry))
        childrenAgg = Some((null, childrenList.toList))
    }
  }

  def splitSettings(ruleInstance: ScalaFormattingRuleInstance, childrenSettings: Seq[FormattingSettings]) {
    childrenAgg match {
      case Some((rule, children)) =>
        throw new IllegalStateException("Split operation failed: the node already has children.")
      case _ =>
        formattingSettings = None
        var childrenList = List[(FormattingSettingsTree, ScalaBlockFormatterEntry)]()
        for (childSetting <- childrenSettings) {
          val fixedEntry = childSetting.instances.get(ruleInstance).get
          assert(fixedEntry.size == 1)
          childrenList = (new FormattingSettingsTree(None, Some(this), Some(childSetting), this.ruleInstances), fixedEntry.head) :: childrenList
        }
        childrenAgg = Some((ruleInstance, childrenList))
    }
  }
}

object FormattingSettingsTree {
  def apply(ruleInstances: Seq[ScalaFormattingRuleInstance], baseSettings: FormattingSettings) =
    new FormattingSettingsTree(formattingSettings = Some(baseSettings),
      parent = None,
      ruleInstances = ruleInstances)
}