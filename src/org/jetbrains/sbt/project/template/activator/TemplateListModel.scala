package org.jetbrains.sbt.project.template.activator

/**
 * User: Dmitry.Naydanov
 * Date: 21.01.15.
 */
class TemplateListModel(val items: Array[(String, ActivatorRepoProcessor.DocData)]) extends JavaAbstractListModel {
  private val temp = 12

  override def getSize: Int = Math.max(items.length, temp)

  override def getElementAtAdapter(i: Int): AnyRef = items(i)._1

  def getAuthorAt(i: Int) = items(i)._2.author

  def getDescriptionAt(i: Int) = items(i)._2.desc

  def getSourceAt(i: Int) = items(i)._2.src

  def getTagsAt(i: Int) = items(i)._2.tags
}