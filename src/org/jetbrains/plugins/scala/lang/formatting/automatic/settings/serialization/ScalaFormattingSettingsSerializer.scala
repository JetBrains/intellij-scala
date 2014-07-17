package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings.serialization

import com.intellij.openapi.components._
import com.intellij.openapi.project.Project
import org.jdom.Element
import scala.collection.mutable
import com.intellij.openapi.util.DefaultJDOMExternalizer
import scala.collection.JavaConversions._
import com.intellij.formatting.WrapType
import java.io.File
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{ScalaBlockFormatterEntry, RuleParentInfo, ScalaFormattingRuleInstance, ScalaFormattingRuleMatcher}
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation.RelationParticipantId

/**
 * A class for (de)serialization of FormattingSettings.
 */
@State(name = "ScalaFormattingSettings",
  storages = Array(new Storage(file = StoragePathMacros.PROJECT_FILE),
    new Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/scalaFormattingSettings.xml",
      scheme = StorageScheme.DIRECTORY_BASED))
)
class ScalaFormattingSettingsSerializer extends PersistentStateComponent[Element] {

  val normalIndentSizeId = "NORMAL_INDENT_SIZE"
  val continuationIndentSizeId = "CONTINUATION_INDENT_SIZE"
  val instancesElementId = "INSTANCES"
  val mapKeyId = "KEY"
  val mapValueId = "VALUE"

  private var instance: Option[FormattingSettings] = None

  def getInstance = instance

  private def loadIntChild(element: Element, id: String) = Integer.parseInt(element.getChild(id).getText)

  private def loadBooleanChild(element: Element, id: String) =
    if (element.getChild(id).getText.toLowerCase == "true") true else false

  /**
   * Builds a bimap of top ruleInstances to Int ids.
   * @param settings
   * @return
   */
  private def buildRuleInstanceIds(settings: FormattingSettings): (mutable.Map[Int, ScalaFormattingRuleInstance], mutable.Map[ScalaFormattingRuleInstance, Int]) = {
    val idToInstance = mutable.Map[Int, ScalaFormattingRuleInstance]()
    val instanceToId = mutable.Map[ScalaFormattingRuleInstance, Int]()
    for ((rootRule, index) <- ScalaFormattingRuleMatcher.topRulesByIds.values.zipWithIndex) {
      val ruleInstance = new ScalaFormattingRuleInstance(None, rootRule, rootRule)
      idToInstance.put(index, ruleInstance)
      instanceToId.put(ruleInstance, index)
    }
    val offset = ScalaFormattingRuleMatcher.topRulesByIds.values.size
    for ((instance, id) <- settings.instances.keys.zipWithIndex) {
      idToInstance.put(id + offset, instance)
      instanceToId.put(instance, id + offset)
    }
    (idToInstance, instanceToId)
  }

  val ruleInstanceRootId = "ROOT"
  val ruleInstanceParentId = "PARENT"
  val ruleInstancePositionId = "POSITION"
  val ruleInstanceRuleId = "RULE"
  val ruleInstanceRuleIdId = "RULE_ID"
  val ruleInstanceRelationId = "RELATION"
  val ruleInstanceRelationIdId = "RELATION_ID"
  val ruleInstanceRelationParicipantIdId = "RELATION_PARTICIPANT_ID"
  val ruleInstanceIdId = "ID"
  val ruleInstanceEntryPrefix = "instance"

  override def loadState(state: Element): Unit = {
    val normalIndentSizeElement = state.getChild(normalIndentSizeId)
    val continuationIndentSizeElement = state.getChild(continuationIndentSizeId)
    val instancesElement = state.getChild(instancesElementId)
    val idsToChildren = mutable.Map[Int, List[Int]]()
    val idsToInstances = mutable.Map[Int, ScalaFormattingRuleInstance]()
    val idsToParentAndPosition = mutable.Map[Int, (Int, Int)]()
    var rootInstanceIds = List[Int]()
    val entriesElement = state.getChild(entriesId)
    var instancesToEntries = Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]()
    for (instanceElement <- instancesElement.getChildren) {
      //load instance
      val rootId = instanceElement.getChild(ruleInstanceRootId).getText
      val ruleElement = instanceElement.getChild(ruleInstanceRuleId)
      val ruleId = ruleElement.getChild(ruleInstanceRuleIdId).getText
      var relations = List[(String, List[RelationParticipantId])]()
      val relationsElement = ruleElement.getChild(ruleInstanceRelationId)
      for (relationElement <- relationsElement.getChildren) {
        val relationId = relationElement.getChild(ruleInstanceRelationIdId).getText
        var participantIds = List[RelationParticipantId]()
        for (participantId <- relationElement.getChildren(ruleInstanceRelationParicipantIdId)) {
          participantIds = participantId.getText :: participantIds
        }
        relations = (relationId, participantIds.reverse) :: relations
      }
      //now build a set from relations
      val relationsSet = Set(relations.map(arg => (rule.getRelationById(arg._1), arg._2)):_*)

      val id = loadIntChild(instanceElement, ruleInstanceIdId)
      val parentElement = instanceElement.getChild(ruleInstanceParentId)
      val positionElement = instanceElement.getChild(ruleInstancePositionId)

      assert(ScalaFormattingRuleMatcher.topRulesByIds.contains(rootId))
      assert(rule.containsRule(ruleId, relationsSet))

      val instance = new ScalaFormattingRuleInstance(None,
        rule.getRule(ruleId, relationsSet),
        ScalaFormattingRuleMatcher.topRulesByIds(rootId))

      assert(!idsToInstances.contains(id))
      idsToInstances.put(id, instance)

      if (parentElement != null && positionElement != null) {
        val parentId = Integer.parseInt(parentElement.getText)
        idsToParentAndPosition.put(id, (parentId, Integer.parseInt(positionElement.getText)))
        idsToChildren.put(parentId, id :: idsToChildren.getOrElse(parentId, List()))
      } else {
        //this is root
        rootInstanceIds = id :: rootInstanceIds
      }
    }

    //build all the instances with proper links to parents by descending from roots
    for (rootId <- rootInstanceIds) {
      if (idsToChildren.contains(rootId)) {
        val instancesToVisit = mutable.Stack[Int](idsToChildren(rootId):_*)
        while (instancesToVisit.nonEmpty) {
          val currentInstanceId = instancesToVisit.pop()
          val currentInstance = idsToInstances(currentInstanceId)
          //load entries
          val instanceEntriesElement = entriesElement.getChild(ruleInstanceEntryPrefix + currentInstanceId.toString)
          val (parentId, position) = idsToParentAndPosition.get(currentInstanceId).get

          val newInstance = new ScalaFormattingRuleInstance(Some(RuleParentInfo(idsToInstances(parentId),position)),
            currentInstance.rule,
            currentInstance.root)

          idsToInstances.put(currentInstanceId, newInstance)

          var entries = List[ScalaBlockFormatterEntry]()
          if (instanceEntriesElement != null) {
            for (entryElement <- instanceEntriesElement.getChildren.toList) {
              entries = loadEntry(entryElement, newInstance) :: entries
            }
          }

          instancesToEntries = instancesToEntries + ((newInstance, entries))
          for (child <- idsToChildren.getOrElse(currentInstanceId, List())) {
            instancesToVisit.push(child)
          }
        }
    }
    }

    //now load indents data
    val normalIndentSize = if (normalIndentSizeElement != null) {
      Some(Integer.parseInt(normalIndentSizeElement.getText))
    } else None
    val continuationIndentSize = if (continuationIndentSizeElement != null) {
      Some(Integer.parseInt(continuationIndentSizeElement.getText))
    } else None

    instance = Some(new FormattingSettings(normalIndentSize, continuationIndentSize, instancesToEntries))
  }

  val alignmentId = "ALIGNMENT"
  val indentInfoId = "INDENT_INFO"
  val indentLengthId = "INDENT_LENGTH"
  val indentRelativeToDirectParentId = "INDENT_RELATIVE_TO_DIRECT_PARENT"
  val indentTypeId = "INDENT_TYPE"


  private def storeIndentInfo(indentInfo: IndentInfo): Element = {
    val res = new Element(indentInfoId)
    res.addContent(new Element(indentLengthId).setText(indentInfo.indentLength.toString))
    res.addContent(new Element(indentRelativeToDirectParentId).setText(indentInfo.indentRelativeToDirectParent.toString))
    if (indentInfo.indentType.isDefined) {
      res.addContent(new Element(indentTypeId).setText(indentInfo.indentType.get.toString))
    }
    res
  }

  private def parseIndentType(text: String) = {
    if (text == IndentType.ContinuationIndent.toString) {
      IndentType.ContinuationIndent
    } else {
      IndentType.NormalIndent
    }
  }

  private def loadIndentInfo(element: Element): Option[IndentInfo] = {
    if (element != null) {
      val indentTypeElement = element.getChild(indentTypeId)
      Some(new IndentInfo(loadIntChild(element, indentLengthId),
        loadBooleanChild(element, indentRelativeToDirectParentId),
        if (indentTypeElement != null) Some(parseIndentType(indentTypeElement.getText)) else None))
    } else None
  }

  val spacingInfoId = "SPACING"
  val spacesCountId = "SPACES_COUNT"
  val minLinebreaksCountId = "MIN_LINEBREAKS"
  val maxLinebreaksCountId = "MAX_LINEBREAKS"
  val actualLinebreaksCountId = "LINEBREAKS"

  private def storeSpacingInfo(info: SpacingInfo): Element = {
    val res = new Element(spacingInfoId)
    res.addContent(new Element(spacesCountId).setText(info.spacesCount.toString))
//    res.addContent(new Element(minLinebreaksCountId).setText(info.minLineBreaksCount.toString))
    info.minLineBreaksCount.map(_.toString).map(new Element(minLinebreaksCountId).setText).map(res.addContent)
//    res.addContent(new Element(maxLinebreaksCountId).setText(info.maxLineBreaksCount.toString))
    info.maxLineBreaksCount.map(_.toString).map(new Element(maxLinebreaksCountId).setText).map(res.addContent)
    res.addContent(new Element(actualLinebreaksCountId).setText(info.lineBreaksCount.toString))
    res
  }

  private def loadSpacingInfo(element: Element): SpacingInfo = {
    val spacesCount = loadIntChild(element, spacesCountId)
    val minLinebreaksCountChild = element.getChild(minLinebreaksCountId)
    val maxLinebreaksCountChild = element.getChild(maxLinebreaksCountId)
    val linebreaksCount = loadIntChild(element, actualLinebreaksCountId)
    new SpacingInfo(spacesCount,
      if (minLinebreaksCountChild != null) Some(Integer.parseInt(minLinebreaksCountChild.getText)) else None,
      if (maxLinebreaksCountChild != null) Some(Integer.parseInt(maxLinebreaksCountChild.getText)) else None,
      linebreaksCount)
  }

  val wrapInfoId = "WRAP"
  val wrapNeededId = "NEED_WRAP"
  val wrapTypeId = "WRAP_TYPE"

  private def storeWrap(wrap: WrapSetting): Element = {
    val res = new Element(wrapInfoId)
    res.addContent(new Element(wrapNeededId).setText(wrap.wrapDefined.toString))
    wrap.wrapType.map((wrapType) => res.addContent(new Element(wrapTypeId).setText(wrapType.toString)))
    res
  }

  private def loadWrap(element: Element): WrapSetting = {
    val wrapTypeElement = element.getChild(wrapTypeId)
    new WrapSetting(loadBooleanChild(element, wrapNeededId),
      if (wrapTypeElement != null) {
        Some(WrapType.valueOf(wrapTypeElement.getText))
      } else None
    )
  }

  val entryRuleInstanceId = "RULE_INSTANCE"
  val originatingFromNoSpaceChildId = "FROM_FIRST_CHILD"

  private def storeEntry(entry: ScalaBlockFormatterEntry, instancesToIds: mutable.Map[ScalaFormattingRuleInstance, Int], name: String): Element = {
    val res = new Element("name")
    res.addContent(storeSpacingInfo(entry.spacing))
    res.addContent(new Element(alignmentId).setText(entry.alignment.needAlignment.toString))
    entry.indentInfo.map(storeIndentInfo).map(res.addContent)
    res.addContent(storeWrap(entry.wrap))
//    res.addContent(new Element(entryRuleInstanceId).setText(instancesToIds(entry.ruleInstance).toString))
    res.addContent(new Element(originatingFromNoSpaceChildId).setText(entry.originatingFromNoSpaceChild.toString))
    res
  }

  private def loadEntry(element: Element, instance: ScalaFormattingRuleInstance): ScalaBlockFormatterEntry = {
    val spacingInfo = loadSpacingInfo(element.getChild(spacingInfoId))
    val alignmentInfo = AlignmentSetting(loadBooleanChild(element, alignmentId))
    val indentInfo = loadIndentInfo(element.getChild(indentInfoId))
    val wrapInfo = loadWrap(element.getChild(wrapInfoId))
    val originatingFromNoSpaceChild = loadBooleanChild(element, originatingFromNoSpaceChildId)
    new ScalaBlockFormatterEntry(spacingInfo, indentInfo, alignmentInfo, wrapInfo, List(), instance, originatingFromNoSpaceChild)
  }

  val entriesId = "ENTRIES"

  def setSettings(settings: FormattingSettings) = {
    instance = Some(settings)
  }

  def getSettings = instance.getOrElse(null)

  override def getState: Element = instance match {
      case Some(settings) =>
        val resElement = new Element("FORMATTING_SETTINGS")
        if (settings.normalIndentSize.isDefined)
          resElement.addContent(new Element(normalIndentSizeId).setText(settings.normalIndentSize.get.toString))
        if (settings.continuationIndentSize.isDefined)
          resElement.addContent(
            new Element(continuationIndentSizeId).setText(settings.continuationIndentSize.get.toString)
          )
        val instancesElement = new Element(instancesElementId)
        val (idsToInstances, instancesToIds) = buildRuleInstanceIds(settings)
        val entriesElement = new Element(entriesId)
        for (id <- idsToInstances.keys) {
          //store instance
          val instanceElement = new Element(ruleInstanceEntryPrefix + id)
          val instance = idsToInstances(id)
          instanceElement.addContent(new Element(ruleInstanceIdId).setText(id.toString))
          instance.parentAndPosition match {
            case Some(parentAndPosition) =>
              instanceElement.addContent(new Element(ruleInstanceParentId).setText(
                instancesToIds(parentAndPosition.parent).toString
              ))
              instanceElement.addContent(new Element(ruleInstancePositionId).setText(
                parentAndPosition.position.toString
              ))
            case None =>
          }
          val rule = instance.rule
          instanceElement.addContent(new Element(ruleInstanceRootId).setText(instance.root.id))
          val ruleElement = new Element(ruleInstanceRuleId)
          ruleElement.addContent(new Element(ruleInstanceRuleIdId).setText(rule.id))
          for ((relation, participantIds) <- rule.relations) {
            val relationElement = new Element(ruleInstanceRelationId)
            relationElement.addContent(new Element(ruleInstanceRelationIdId).setText(relation.id))
            for (participantId <- participantIds) {
              relationElement.addContent(new Element(ruleInstanceRelationParicipantIdId).setText(participantId))
            }
            ruleElement.addContent(relationElement)
          }
          instanceElement.addContent(ruleElement)
          if (settings.instances.contains(instance)) {
            //this is not a root rule and it has some entries linked to it
            val instanceEntriesElement = new Element("instance" + id.toString)
            for ((entry, index) <- settings.instances(instance).zipWithIndex) {
              assert(entry.rule == instance)
              instanceEntriesElement.addContent(storeEntry(entry, instancesToIds, "entry"+index))
            }
            entriesElement.addContent(instanceEntriesElement)
          }
//          instanceElement.addContent(entriesElement)
          instancesElement.addContent(instanceElement)
        }
        resElement.addContent(instancesElement)
        resElement.addContent(entriesElement)
        resElement
      case None => null
    }

//  override def getPresentableName: String = "Scala Formatting Settings"
//
//  override def getExportFiles: Array[File] = Array[File](PathManager.getOptionsFile("scalaFormattingSettings"))
}

object ScalaFormattingSettingsSerializer {

  def getInstance(project: Project): ScalaFormattingSettingsSerializer = ServiceManager.getService(project, classOf[ScalaFormattingSettingsSerializer])
}
