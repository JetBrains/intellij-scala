package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.IdeBorderFactory
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.scala.ScalaBundle

import java.lang.reflect.Field
import java.util.Objects
import javax.swing._
import scala.collection.immutable.ListMap

final class TrailingCommaPanel(settings: CodeStyleSettings) extends ScalaCodeStyleSubPanelBase(settings) {

  import ScalaCodeStyleSettings.TrailingCommaMode
  import TrailingCommaPanel.ComboBoxItem

  private var innerPanel: JPanel = _

  private var trailingCommaModeSelector: ComboBox[ComboBoxItem[TrailingCommaMode]] = _
  private var trailingCommaModeSelectorModel: DefaultComboBoxModel[ComboBoxItem[TrailingCommaMode]] = _
  private var trailingCommaScopePanel: JPanel = _
  private var scopeCheckboxes: Seq[(JCheckBox, Field)] = _

  private val scopeFields: ListMap[String, String] = ListMap(
    ("TRAILING_COMMA_ARG_LIST_ENABLED", ScalaBundle.message("trailing.comma.panel.scope.arguments.list")),
    ("TRAILING_COMMA_PARAMS_ENABLED", ScalaBundle.message("trailing.comma.panel.scope.parameters.list")),
    ("TRAILING_COMMA_TUPLE_ENABLED", ScalaBundle.message("trailing.comma.panel.scope.tuple")),
    ("TRAILING_COMMA_PATTERN_ARG_LIST_ENABLED", ScalaBundle.message("trailing.comma.panel.scope.pattern.arguments.list")),
    ("TRAILING_COMMA_TYPE_PARAMS_ENABLED", ScalaBundle.message("trailing.comma.panel.scope.type.parameters.list")),
    ("TRAILING_COMMA_IMPORT_SELECTOR_ENABLED", ScalaBundle.message("trailing.comma.panel.scope.import.selector")),
  )

  override def apply(settings: CodeStyleSettings): Unit = {
    if (!isModified(settings)) return
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    selectedTrailingCommaMode.foreach { mode =>
      scalaSettings.TRAILING_COMMA_MODE = mode
    }

    scopeCheckboxes.foreach { case (cb, field) =>
      field.set(scalaSettings, cb.isSelected)
    }
  }

  override def isModified(settings: CodeStyleSettings): Boolean = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    !selectedTrailingCommaMode.contains(scalaSettings.TRAILING_COMMA_MODE) ||
      scopeCheckboxes.exists { case (cb, field) =>
        field.get(scalaSettings).asInstanceOf[Boolean] != cb.isSelected
      }
  }

  override def resetImpl(settings: CodeStyleSettings): Unit = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val fictiveItem = new ComboBoxItem(scalaSettings.TRAILING_COMMA_MODE, "")
    trailingCommaModeSelector.setSelectedIndex(trailingCommaModeSelectorModel.getIndexOf(fictiveItem))
    scopeCheckboxes.foreach { case (cb, field) =>
      cb.setSelected(field.get(scalaSettings).asInstanceOf[Boolean])
    }
  }

  override def getPanel: JPanel = {
    if (innerPanel == null) {
      innerPanel = buildInnerPanel()
    }
    innerPanel
  }

  private def buildInnerPanel(): JPanel = {
    import GridConstraints._
    val panel = new JPanel
    panel.setLayout(new GridLayoutManager(2, 3, JBUI.emptyInsets(), -1, -1))
    panel.setBorder(IdeBorderFactory.createTitledBorder(ScalaBundle.message("trailing.comma.panel.title")))

    trailingCommaModeSelectorModel = new DefaultComboBoxModel(Array(
      new ComboBoxItem(TrailingCommaMode.TRAILING_COMMA_KEEP, ScalaBundle.message("trailing.comma.panel.keep")),
      new ComboBoxItem(TrailingCommaMode.TRAILING_COMMA_REMOVE_WHEN_MULTILINE, ScalaBundle.message("trailing.comma.panel.remove.when.multiline")),
      new ComboBoxItem(TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE, ScalaBundle.message("trailing.comma.panel.add.when.multiline")),
    ))

    trailingCommaModeSelector = new ComboBox[ComboBoxItem[TrailingCommaMode]]
    trailingCommaModeSelector.setModel(trailingCommaModeSelectorModel)
    panel.add(trailingCommaModeSelector, new GridConstraints(0, 0, 1, 1, ANCHOR_WEST, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED, null, null, null, 0, false))
    panel.add(new Spacer, new GridConstraints(0, 1, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_WANT_GROW, null, null, null, 0, false))
    panel.add(new Spacer, new GridConstraints(0, 2, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_WANT_GROW, null, null, null, 0, false))

    trailingCommaScopePanel = new JPanel
    val gridRows = 2
    val gridCols = Math.ceil(scopeFields.size.toDouble / gridRows).toInt
    trailingCommaScopePanel.setLayout(new GridLayoutManager(gridRows, gridCols, JBUI.emptyInsets(), -1, -1))
    panel.add(trailingCommaScopePanel, new GridConstraints(1, 0, 1, 2, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED, null, null, null, 0, false))
    panel.add(new Spacer, new GridConstraints(1, 2, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_WANT_GROW, null, null, null, 0, false))

    val styleClass = classOf[ScalaCodeStyleSettings]
    scopeCheckboxes = scopeFields.toSeq.map { case (fieldName, title) =>
      val cb = new JCheckBox
      cb.setText(title)
      (cb, styleClass.getField(fieldName))
    }

    scopeCheckboxes.zipWithIndex.foreach { case ((cb, _), idx) =>
      val (row, col) = (idx % gridRows, idx / gridRows)
      trailingCommaScopePanel.add(cb, new GridConstraints(row, col, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED, null, null, null, 0, false))
    }

    trailingCommaModeSelector.addActionListener { _ =>
      val isScopeEnabled = selectedTrailingCommaMode.exists(_ != TrailingCommaMode.TRAILING_COMMA_KEEP)
      scopeCheckboxes.foreach(_._1.setEnabled(isScopeEnabled))
    }

    panel
  }

  private def selectedTrailingCommaMode: Option[TrailingCommaMode] = {
    Option(trailingCommaModeSelector.getSelectedItem)
      .map(_.asInstanceOf[ComboBoxItem[TrailingCommaMode]].value)
  }
}

private object TrailingCommaPanel {
  // helper class to make JComboBox use custom display value for enum entries, not default Enum.toString
  class ComboBoxItem[E <: java.lang.Enum[E]](val value: E, val displayValue: String) {
    override def toString: String = displayValue
    override def equals(o: Any): Boolean = {
      o match {
        case that: ComboBoxItem[_] =>
          Objects.equals(value, that.value)
        case _ =>
          false
      }
    }
    override def hashCode: Int = value.##
  }
}
