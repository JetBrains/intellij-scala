package org.jetbrains.sbt.project.template.techhub

import javax.swing.AbstractListModel

import org.jetbrains.sbt.project.template.techhub.TechHubStarterProjects.IndexEntry

class TemplateListModel(val items: Array[IndexEntry])
  extends AbstractListModel[IndexEntry] {
  private val temp = 12
  
  /**
    * We have to preserve both index and id in order to
    * be able extract any item in correct order (for display in the list, in lex order) and 
    * to extract particular selected item. We can't relay on index only because filtering feature in the UI uses
    * model changing (so new model will probably have other amount of items and other indices). Also we can't preserve
    * id's only as we want to show items in actual order without ugly hacks like using LinkedHashSet 
    */
  private val indexedItems = items.sortBy(a => a.displayName)

  override def getSize: Int = Math.max(indexedItems.length, temp)
  override def getElementAt(index: Int): IndexEntry = getItem(index)

  private def getItem(i: Int): IndexEntry =
    if (i < indexedItems.length && i > -1) indexedItems(i)
    else TechHubStarterProjects.dummyEntry

}
