package org.jetbrains.sbt.project.template.techhub

import org.jetbrains.plugins.scala.util.ui.extensions.JListOps

class TechHubTemplateList(items: Array[IndexEntry]) extends TechHubTemplateListBase(items) {

  def setSelectedTemplateEnsuring(templateName: String): Unit = {
    val items: Seq[IndexEntry] = list1.items
    val templateNames = items.map(_.displayName)
    val index = templateNames.indexWhere(_ == templateName)
    if (index == -1) {
      throw new AssertionError(
        s"""Template '$templateName' is not available among templates:
           |${templateNames.zipWithIndex.map(_.swap).mkString("\n")}""".stripMargin)
    }
    list1.setSelectedIndex(index)
  }
}
