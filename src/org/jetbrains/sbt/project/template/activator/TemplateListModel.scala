package org.jetbrains.sbt.project.template.activator

import org.jetbrains.sbt.project.template.activator.ActivatorRepoProcessor.DocData

/**
 * User: Dmitry.Naydanov
 * Date: 21.01.15.
 */
class TemplateListModel(val items: Array[(String, ActivatorRepoProcessor.DocData)]) extends JavaAbstractListModel {
  private val temp = 12
  private val dumbItem = ("", ActivatorRepoProcessor.DocData("", "", "", "", "", "", ""))
  private val sortedItems = items.sortBy(a => a._2.title)

  override def getSize: Int = Math.max(sortedItems.length, temp)

  override def getElementAtAdapter(i: Int): AnyRef = getItem(i)._2.title

  def getAuthorAt(i: Int) = getItem(i)._2.author

  def getDescriptionAt(i: Int) = getItem(i)._2.desc

  def getSourceAt(i: Int) = getItem(i)._2.src

  def getTagsAt(i: Int) = getItem(i)._2.tags

  def getId(i: Int) = getItem(i)._1

  private def getItem(i: Int): (String, DocData) = if (i < sortedItems.length && i > -1) sortedItems(i) else dumbItem
}