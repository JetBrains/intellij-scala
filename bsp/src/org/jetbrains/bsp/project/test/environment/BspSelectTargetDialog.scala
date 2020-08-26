package org.jetbrains.bsp.project.test.environment

import java.awt.{BorderLayout, Toolkit}
import java.net.URI

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{ComboBox, DialogWrapper}
import javax.swing.{JComponent, JPanel, SwingConstants}
import org.jetbrains.bsp.BspBundle

object BspSelectTargetDialog {
  def promptForBspTarget(
    project: Project,
    targetIds: collection.Seq[BuildTargetIdentifier],
    selected: Option[BuildTargetIdentifier]
  ): Option[BuildTargetIdentifier] = {
    var result: Option[BuildTargetIdentifier] = None
    val dialog = new BspSelectTargetDialog(project, targetIds, selected)
    if (dialog.showAndGet()) {
      result = dialog.selectedItem
    }
    result
  }

  private[environment] def visibleNames(targetIds: collection.Seq[BuildTargetIdentifier]): collection.Seq[String] = {
    targetIds.map(visibleName)
  }

  private def visibleName(id: BuildTargetIdentifier) = {
    getQueryAsMap(new URI(id.getUri)).get("id").flatten.getOrElse(id.getUri)
  }

  private def getQueryAsMap(uri: URI): Map[String, Option[String]] = {
    uri.getQuery.split("&")
      .map(_.split("=", 2))
      .map(item => item(0) -> item.lift(1)).toMap
  }
}

class BspSelectTargetDialog(
  project: Project,
  targetIds: collection.Seq[BuildTargetIdentifier],
  selected: Option[BuildTargetIdentifier]
) extends DialogWrapper(project, true) {
  setTitle(BspBundle.message("bsp.task.choose.target.title"))
  setButtonsAlignment(SwingConstants.CENTER)
  setOKButtonText(CommonBundle.getOkButtonText)
  var combo: ComboBox[String] = new ComboBox[String]()
  init()

  def selectedItem: Option[BuildTargetIdentifier] = targetIds.lift(combo.getSelectedIndex)

  override def createCenterPanel(): JComponent = null

  override def createNorthPanel(): JComponent = {
    val selectedIndex = selected.map(targetIds.indexOf(_)).getOrElse(-1)
    combo.setEditable(false)
    BspSelectTargetDialog.visibleNames(targetIds).foreach(combo.addItem)
    combo.setSelectedIndex(selectedIndex)
    combo.setMinimumAndPreferredWidth(Toolkit.getDefaultToolkit.getScreenSize.width / 4)
    val panel = new JPanel(new BorderLayout)
    panel.add(combo)
    panel
  }
}