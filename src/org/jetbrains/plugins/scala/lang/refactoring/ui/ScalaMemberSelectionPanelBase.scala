package org.jetbrains.plugins.scala
package lang.refactoring.ui

import com.intellij.psi.PsiElement
import java.util
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import java.awt.BorderLayout
import javax.swing.JScrollPane
import com.intellij.ui.{SeparatorFactory, ScrollPaneFactory}
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaMemberSelectionTableBase

/**
 * Nikolay.Tropin
 * 8/20/13
 */
abstract class ScalaMemberSelectionPanelBase[M <: PsiElement, I <: ScalaMemberInfoBase[M]](title: String,
                                                                                           memberInfo: util.List[I],
                                                                                           abstractColumnHeader: String)
        extends AbstractMemberSelectionPanel[M, I] {

  setLayout(new BorderLayout)
  private val myTable = createMemberSelectionTable(memberInfo, abstractColumnHeader)
  val scrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(myTable)
  add(SeparatorFactory.createSeparator(title, myTable), BorderLayout.NORTH)
  add(scrollPane, BorderLayout.CENTER)

  def createMemberSelectionTable(memberInfos: util.List[I], abstractColumnHeader: String): ScalaMemberSelectionTableBase[M, I]

  def getTable: ScalaMemberSelectionTableBase[M, I] = myTable

}
