package org.jetbrains.bsp.project.test.environment

import java.awt.{BorderLayout, Toolkit}
import java.net.URI

import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{ComboBox, DialogWrapper}
import javax.swing.{JComponent, JLabel, JPanel, SwingConstants}
import org.jetbrains.bsp.BspBundle

object  BspSelectTargetDialog {
  def promptForBspTarget(project: Project, targetIds: Seq[URI], selected: Option[URI]): Option[URI] = {
    var result: Option[URI] = None
    val dialog = new BspSelectTargetDialog(project, targetIds, selected)
    if(dialog.showAndGet()) {
      result = dialog.selectedItem
    }
    result
  }
}
class BspSelectTargetDialog(project: Project, targetIds: Seq[URI], selected: Option[URI]) extends DialogWrapper(project, true) {
  setTitle(BspBundle.message("bsp.task.choose.target.title"))
  setButtonsAlignment(SwingConstants.CENTER)
  setOKButtonText(CommonBundle.getOkButtonText)
  var combo: ComboBox[String] = new ComboBox[String]()
  init()

  def selectedItem: Option[URI] = targetIds.lift(combo.getSelectedIndex)

  def uriQueryToMap(query: String): Map[String, Option[String]] =
    query.split("&")
      .map(_.split("=", 2))
      .map(item => item(0) -> item.lift(1)).toMap

  def visibleNames: Seq[String] = targetIds.flatMap(uri => uriQueryToMap(uri.getQuery).get("id")).toList.flatten

  override def createCenterPanel(): JComponent = null

  override def createNorthPanel(): JComponent = {
    val selectedIndex = selected.map(targetIds.indexOf(_)).getOrElse(-1)
    combo.setEditable(false)
    visibleNames.foreach(combo.addItem)
    combo.setSelectedIndex(selectedIndex)
    combo.setMinimumAndPreferredWidth(Toolkit.getDefaultToolkit.getScreenSize.width / 4)
    val panel = new JPanel(new BorderLayout)
    panel.add(combo)
    panel
  }
}