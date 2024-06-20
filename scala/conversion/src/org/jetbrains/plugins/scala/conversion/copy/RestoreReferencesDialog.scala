package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.CommonBundle
import com.intellij.ide.util.FQNameCellRenderer
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, VerticalFlowLayout}
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.{JBLabel, JBList}
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.ComponentStyle.SMALL
import com.intellij.util.ui.UIUtil.FontColor.BRIGHTER

import java.awt._
import java.util
import java.util.Collections
import javax.swing._

class RestoreReferencesDialog(
  project: Project,
  private val myNamedElements: Array[String]
) extends DialogWrapper(project, true) {

  override protected def getDimensionServiceKey = "#com.intellij.codeInsight.editorActions.RestoreReferencesDialog"

  private var myList: JList[String] = _
  private var mySelectedElements: java.util.List[String] = Collections.emptyList
  def getSelectedElements: util.List[String] = mySelectedElements

  setTitle(JavaBundle.message("dialog.import.on.paste.title2"))

  init()

  myList.setSelectionInterval(0, myNamedElements.length - 1)

  override protected def doOKAction(): Unit = {
    mySelectedElements = myList.getSelectedValuesList
    super.doOKAction()
  }

  override protected def createCenterPanel: JComponent = {
    val panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP))
    myList = new JBList[String](myNamedElements: _*)
    myList.setCellRenderer(new FQNameCellRenderer)
    panel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER)
    panel.add(new JBLabel(JavaBundle.message("dialog.paste.on.import.text2"), SMALL, BRIGHTER), BorderLayout.NORTH)
    val buttonPanel = new JPanel(new VerticalFlowLayout)
    val okButton = new JButton(CommonBundle.getOkButtonText)
    getRootPane.setDefaultButton(okButton)
    buttonPanel.add(okButton)
    val cancelButton = new JButton(CommonBundle.getCancelButtonText)
    buttonPanel.add(cancelButton)
    panel.setPreferredSize(new Dimension(500, 400))
    panel
  }
}