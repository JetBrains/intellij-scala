package org.jetbrains.sbt.project.template.activator

import org.jetbrains.sbt.project.template.activator.ActivatorRepoProcessor.DocData
import org.jetbrains.sbt.project.template.activator.TemplateListModel.FullTemplateData

/**
 * User: Dmitry.Naydanov
 * Date: 21.01.15.
 */
class TemplateListModel(val items: Array[(String, ActivatorRepoProcessor.DocData)]) extends JavaAbstractListModel {
  private val temp = 12
  
  private val dumbData = ActivatorRepoProcessor.DocData("", "", "", "", "", "", "")
  private val dumbFullData = FullTemplateData("", dumbData)
  
  /**
    * We have to preserve both index and id in order to
    * be able extract any item in correct order (for display in the list, in lex order) and 
    * to extract particular selected item. We can't relay on index only because filtering feature in the UI uses
    * model changing (so new model will probably have other amount of items and other indices). Also we can't preserve
    * id's only as we want to show items in actual order without ugly hacks like using LinkedHashSet 
    */
  private val (indexedItems, idToItem) = {
    val a1 = items.sortBy(a => a._2.title)
    (a1.map{case (a, b) => FullTemplateData(a, b)}, a1.toMap)
  }

  override def getSize: Int = Math.max(indexedItems.length, temp)

  override def getElementAtAdapter(i: Int): AnyRef = getItem(i)

  def getId(i: Int): String = getItem(i).id

  def getAuthorAt(id: String): String = getData(id).author

  def getDescriptionAt(id: String): String = getData(id).desc

  def getSourceAt(id: String): String = getData(id).src

  def getTagsAt(id: String): String = getData(id).tags

  private def getItem(i: Int): FullTemplateData = if (i < indexedItems.length && i > -1) indexedItems(i) else dumbFullData
  
  private def getData(id: String) = idToItem.getOrElse(id, dumbData)
}

object TemplateListModel {
  case class FullTemplateData(id: String, docData: DocData) {
    override def toString: String = docData.toString
  }
}