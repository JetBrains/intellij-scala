package org.jetbrains.plugins.scala.project

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.IdeBorderFactory
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.scala.project.ScalaLibraryEditorForm._

import java.awt._
import javax.swing._

class ScalaLibraryEditorForm() {

  final private val myClasspathEditor = new MyPathEditor(new FileChooserDescriptor(true, false, true, true, false, true))

  private val myClasspathPanel: JPanel = {
    val panel = new JPanel
    panel.setLayout(new BorderLayout(0, 0))
    panel.setBorder(IdeBorderFactory.createBorder)
    panel.add(myClasspathEditor.createComponent, BorderLayout.CENTER)
    panel
  }

  private val myLanguageLevel: ComboBox[ScalaLanguageLevel] = {
    val combo = new ComboBox[ScalaLanguageLevel]
    combo.setRenderer(new NonNullableValueBasedListRenderer[ScalaLanguageLevel](_.getVersion))
    combo.setModel(new DefaultComboBoxModel[ScalaLanguageLevel](publishedScalaLanguageLevels))
    combo
  }

  val contentPanel: JPanel = {
    val panel = new JPanel(new GridLayoutManager(5, 3, JBUI.emptyInsets, -1, -1))

    panel.add(new JLabel("Scala version:"), new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false))
    panel.add(myLanguageLevel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false))
    panel.add(new Spacer, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false))

    panel.add(new Spacer, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 5), new Dimension(-1, 5), new Dimension(-1, 5), 0, false))

    panel.add(new JLabel("Compiler classpath:"), new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), null, null, 1, false))
    panel.add(myClasspathPanel, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false))

    panel.add(new JLabel("Standard library:"), new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), null, null, 1, false))
    panel
  }

  def languageLevel: ScalaLanguageLevel = myLanguageLevel.getSelectedItem.asInstanceOf[ScalaLanguageLevel]
  def languageLevel_=(languageLevel: ScalaLanguageLevel): Unit = {
    // in case some new major release candidate version of the scala compiler is used
    // we want to display it's language level anyway (it's not added to the combobox when creating new SDK)
    val items = myLanguageLevel.items
    if (!items.contains(languageLevel)) {
      myLanguageLevel.setModel(new DefaultComboBoxModel[ScalaLanguageLevel]((languageLevel +: items).toArray))
    }
    myLanguageLevel.setSelectedItem(languageLevel)
  }

  def classpath: Array[String] = myClasspathEditor.getPaths
  def classpath_=(classpath: Array[String]): Unit = myClasspathEditor.setPaths(classpath)
}

private object ScalaLibraryEditorForm {
  private val publishedScalaLanguageLevels = ScalaLanguageLevel.publishedVersions.reverse

  implicit class ComboBoxOps[T](private val target: ComboBox[T]) extends AnyVal {
    def items: Seq[T] = {
      val model = target.getModel
      Seq.tabulate(model.getSize)(model.getElementAt)
    }
  }
}