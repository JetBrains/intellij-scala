package org.jetbrains.sbt.project.settings

import com.intellij.util.ui.UI
import javax.swing.{JCheckBox, JComponent, JLabel}
import org.jetbrains.annotations.Nls
import org.jetbrains.sbt.SbtBundle

class SbtExtraControlsEx extends SbtExtraControls {

  var resolveClassifiersCheckBox: JCheckBox = _
  var resolveSbtClassifiersCheckBox: JCheckBox = _
  var useSbtShellForImportCheckBox: JCheckBox = _
  var useSbtShellForBuildCheckBox: JCheckBox = _
  var remoteDebugSbtShellCheckBox: JCheckBox = _
  var allowSbtVersionOverrideCheckBox: JCheckBox = _

  private def withTooltip(component: JComponent, @Nls tooltip: String) =
    UI.PanelFactory.panel(component).withTooltip(tooltip).createPanel()

  override def createUIComponents(): Unit = {

    resolveClassifiersCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.resolveClassifiers"))
    resolveSbtClassifiersCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.resolveSbtClassifiers"))
    useSbtShellForImportCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.useShellForImport"))
    useSbtShellForBuildCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.useShellForBuild"))
    remoteDebugSbtShellCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.remoteDebug"))
    allowSbtVersionOverrideCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.allowSbtVersionOverride"))


    downloadSources = withTooltip(resolveClassifiersCheckBox, SbtBundle.message("sbt.settings.resolveClassifiers.tooltip"))
    downloadSbtSources = withTooltip(resolveSbtClassifiersCheckBox, SbtBundle.message("sbt.settings.resolveSbtClassifiers.tooltip"))

    useShellForReload = withTooltip(useSbtShellForImportCheckBox, SbtBundle.message("sbt.settings.useShellForImport.tooltip"))
    useShellForBuilds = withTooltip(useSbtShellForBuildCheckBox, SbtBundle.message("sbt.settings.useShellForBuild.tooltip"))

    allowVersionOverride = withTooltip(allowSbtVersionOverrideCheckBox, SbtBundle.message("sbt.settings.remoteDebug.tooltip"))
    enableDebugging = withTooltip(remoteDebugSbtShellCheckBox, SbtBundle.message("sbt.settings.allowSbtVersionOverride.tooltip"))

  }

}
