package org.jetbrains.plugins.scala.project.template

import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import com.intellij.ui.components.{ComponentsKt, JBTextField}
import com.intellij.util.ui.UI
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.ScalaLibraryType

import javax.swing.{JLabel, JPanel}

trait ScalaSDKStepLike extends PackagePrefixStepLike {

  protected def librariesContainer: LibrariesContainer

  //noinspection ScalaExtractStringToBundle
  @Nls
  protected val scalaSdkLabelText: String = "Scala S\u001BDK:"

  protected lazy val libraryPanel = new LibraryOptionsPanel(
    ScalaLibraryType.Description,
    "",
    FrameworkLibraryVersionFilter.ALL,
    librariesContainer,
    false
  )
}

trait PackagePrefixStepLike {

  protected val packagePrefixTextField: JBTextField = {
    val tf = new JBTextField()
    tf.getEmptyText.setText(ScalaBundle.message("package.prefix.example"))
    tf
  }

  protected val packagePrefixHelpText: String = ScalaBundle.message("package.prefix.help")

  protected val packagePrefixPanelWithTooltip: JPanel = UI.PanelFactory
    .panel(packagePrefixTextField)
    .withTooltip(packagePrefixHelpText)
    .createPanel()

  protected val packagePrefixLabelText: String = ScalaBundle.message("package.prefix.label")

  /**
   * In NewProjectWizard we can't use `prefixPanel` created with  `UI.PanelFactory.panel.withTooltip`
   * because it adds some strange indent to the left of the panel, which looks ugly.<br>
   * I didn't find a nice way to fix this, so we set tooltip on a label when using NPW
   */
  protected val packagePrefixLabel: JLabel = {
    val label = ComponentsKt.Label(packagePrefixLabelText, null, null, false, null)
    label.setToolTipText(packagePrefixHelpText)
    label
  }

}