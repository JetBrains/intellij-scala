package org.jetbrains.sbt.project.template.activator

/**
 * User: Dmitry.Naydanov
 * Date: 21.01.15.
 */
class TemplateListModel(val items: Array[(String, ActivatorRepoProcessor.DocData)]) extends JavaAbstractListModel {
  private val sortedItems = items.sortBy(a => a._2.title)
  private val temp = 12

  override def getSize: Int = Math.max(sortedItems.length, temp)

  override def getElementAtAdapter(i: Int): AnyRef = sortedItems(i)._2.title

  def getAuthorAt(i: Int) = sortedItems(i)._2.author

  def getDescriptionAt(i: Int) = sortedItems(i)._2.desc

  def getSourceAt(i: Int) = sortedItems(i)._2.src

  def getTagsAt(i: Int) = sortedItems(i)._2.tags
}