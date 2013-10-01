package org.jetbrains.plugins.scala
package codeInsight.generation.ui

import java.awt.BorderLayout
import javax.swing.JScrollPane
import com.intellij.ui.{SeparatorFactory, ScrollPaneFactory}
import java.util
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel

/**
 * Nikolay.Tropin
 * 8/20/13
 */
class ScalaMemberSelectionPanel(title: String, memberInfo: util.List[ScalaMemberInfo], abstractColumnHeader: String)
        extends AbstractMemberSelectionPanel[ScNamedElement, ScalaMemberInfo] {

  setLayout(new BorderLayout)
  private val myTable = createMemberSelectionTable(memberInfo, abstractColumnHeader)
  val scrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(myTable)
  add(SeparatorFactory.createSeparator(title, myTable), BorderLayout.NORTH)
  add(scrollPane, BorderLayout.CENTER)

  protected def createMemberSelectionTable(memberInfos: util.List[ScalaMemberInfo], abstractColumnHeader: String): ScalaMemberSelectionTable =
    new ScalaMemberSelectionTable(memberInfos, null, abstractColumnHeader)

  def getTable: ScalaMemberSelectionTable = myTable

}

