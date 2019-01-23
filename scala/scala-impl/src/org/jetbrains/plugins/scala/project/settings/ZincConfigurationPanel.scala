package org.jetbrains.plugins.scala.project.settings

import java.awt.{BorderLayout, GridBagConstraints, GridBagLayout}
import java.util.regex.Pattern

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{InputValidator, Messages}
import com.intellij.ui.components.JBList
import com.intellij.ui.{AnActionButton, AnActionButtonRunnable, IdeBorderFactory, ToolbarDecorator}
import javax.swing.{DefaultListModel, JCheckBox, JPanel}

import scala.collection.JavaConverters._
import scala.util.Try

abstract class ListPanel(title: String, emptyText: String) {
  private val model = new DefaultListModel[String]
  private val list = new JBList[String](model)

  def askForNewListItem(): Option[String]

  def items_=(values: Seq[String]): Unit = {
    model.removeAllElements()
    values.foreach(model.addElement)
  }

  def items: Seq[String] = {
    model.elements().asScala.toSeq
  }

  private val removeAction = new AnActionButtonRunnable {
    override def run(t: AnActionButton): Unit = {
      list.getSelectedValuesList.toArray.foreach(model.removeElement)
    }
  }

  private val addAction = new AnActionButtonRunnable {
    override def run(t: AnActionButton): Unit = {
      askForNewListItem().foreach { str =>
        model.addElement(str)
      }
    }
  }

  val panel: JPanel = ToolbarDecorator
    .createDecorator(list)
    .setAddAction(addAction)
    .setRemoveAction(removeAction)
    .disableUpDownActions()
    .createPanel()
  panel.setBorder(IdeBorderFactory.createTitledBorder(title))
  list.setEmptyText(emptyText)
}

class ZincConfigurationPanel(project: Project) {

  def compileToJar: Boolean = compileToJarCheckbox.isSelected
  def compileToJar_=(value: Boolean): Unit = compileToJarCheckbox.setSelected(value)

  def enableIgnoringScalacOptions: Boolean = enableIgnoringScalacOptionsCheckbox.isSelected
  def enableIgnoringScalacOptions_=(value: Boolean): Unit = enableIgnoringScalacOptionsCheckbox.setSelected(value)

  def ignoredScalacOptions: java.util.List[String] = ignoredScalacOptionsPanel.items.asJava
  def ignoredScalacOptions_=(value: java.util.List[String]): Unit = ignoredScalacOptionsPanel.items = value.asScala

  val contentPanel = new JPanel

  private val compileToJarCheckbox = new JCheckBox("Compile to jar")
  private val enableIgnoringScalacOptionsCheckbox = new JCheckBox("Enable ignoring scalac options")
  private val ignoredScalacOptionsPanel = new ListPanel(title = "Ignored scalac options", emptyText = "No ignored scalac options") {
    override def askForNewListItem(): Option[String] = {
      val message =
        """
          |<html><body>
          |  Specify ignored scalac options. If scalac option changes, it triggers full recompilation, unless it is ignored.<br/>
          |  These options can be specified using Java regular expressions.<br/>
          |  Examples:
          |  <ul>
          |    <strong>-Xfatal-warnings</strong> Ignore -Xfatal-warnings flag<br/>
          |    <strong>-Xprint:.*</strong> Ignore all -Xprint flags like -Xprint:typer<br/>
          |    <strong>-encoding .*</strong> Ignore -encoding flag with any argument<br/>
          |  </ul>
          |</body></html>
      """.stripMargin.replace("\r", "").replace("\n", "")

      val regexInputValidator = new InputValidator {
        override def checkInput(inputString: String): Boolean = Try(Pattern.compile(inputString)).isSuccess
        override def canClose(inputString: String): Boolean = true
      }

      val input = Messages.showInputDialog(message, "Ignore", null, null, regexInputValidator)
      Option(input)
    }
  }

  private def northWest = {
    val c = new GridBagConstraints
    c.anchor = GridBagConstraints.NORTHWEST
    c.weightx = 1
    c.gridx = 1
    c.gridy = GridBagConstraints.RELATIVE
    c
  }
  private def northWestFillHorizontal = {
    val c = northWest
    c.fill = GridBagConstraints.HORIZONTAL
    c
  }

  private val innerPanel = new JPanel
  innerPanel.setLayout(new GridBagLayout)
  innerPanel.add(compileToJarCheckbox, northWest)
  innerPanel.add(enableIgnoringScalacOptionsCheckbox, northWest)
  innerPanel.add(ignoredScalacOptionsPanel.panel, northWestFillHorizontal)

  contentPanel.setLayout(new BorderLayout)
  contentPanel.add(innerPanel, BorderLayout.NORTH)

}
