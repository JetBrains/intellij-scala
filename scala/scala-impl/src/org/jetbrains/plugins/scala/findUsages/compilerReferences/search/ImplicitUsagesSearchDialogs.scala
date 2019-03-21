package org.jetbrains.plugins.scala.findUsages.compilerReferences.search

import java.awt.event.ActionEvent

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.{Action, JComponent, JLabel}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SearchTargetExtractors.UsageType
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.{CompilerIndicesConfigurable, CompilerIndicesSbtSettings, CompilerIndicesSettings}

private object ImplicitUsagesSearchDialogs {
  class EnableCompilerIndicesDialog(project: Project, canBeParent: Boolean, usageType: UsageType)
    extends DialogWrapper(project, canBeParent) {

    private[this] val settingsLink =
      new LinkLabel[AnyRef](ScalaBundle.message("scala.compiler.indices.settings.navigate"), null) {
        setListener({
          case (_, _) =>
            close(DialogWrapper.CLOSE_EXIT_CODE)
            ShowSettingsUtil.getInstance().showSettingsDialog(project, classOf[CompilerIndicesConfigurable])
        }, null)
      }

    private[this] val description = new JLabel(
      s"""|<html>Searching for $usageType usages requires bytecode indices to be enabled.<br>
          |Do you want to enable bytecode indexing (takes effect after IDEA restart)?</html>""".stripMargin)

    setTitle("Enable Bytecode Indexing")
    setResizable(false)
    init()

    override def createCenterPanel(): JComponent =
      FormBuilder.createFormBuilder()
        .addComponent(description)
        .addComponent(settingsLink)
        .getPanel

    override def createActions(): Array[Action] = {
      def enableCompilerIndices(): Unit = CompilerIndicesSettings(project).setIndexingEnabled(true)

      val enable = new DialogWrapperAction("Enable") {
        override def doAction(e: ActionEvent): Unit = {
          enableCompilerIndices()
          close(DialogWrapper.OK_EXIT_CODE)
        }
      }

      val enableAndRestart = new DialogWrapperAction("Enable and restart") {
        override def doAction(e: ActionEvent): Unit = {
          enableCompilerIndices()
          ApplicationManagerEx.getApplicationEx.restart(true)
          close(DialogWrapper.OK_EXIT_CODE)
        }
      }

      Array(enableAndRestart, enable, getCancelAction)
    }
  }

  class ImplicitFindUsagesDialog(
    canBeParent: Boolean,
    element:     PsiNamedElement,
    title:       String
  ) extends DialogWrapper(element.getProject, canBeParent, DialogWrapper.IdeModalityType.PROJECT) {

    private[this] val shouldCompileCB = new JBCheckBox(
      "Pre-compile modules in use scope before searching",
      !CompilerIndicesSbtSettings().useManualConfiguration
    )

    private[this] val settingsLink =
      new LinkLabel[AnyRef]("Settings", null) {
        setListener({
          case (_, _) =>
            ShowSettingsUtil.getInstance().showSettingsDialog(element.getProject, classOf[CompilerIndicesConfigurable])
        }, null)
      }

    def shouldCompile: Boolean = shouldCompileCB.isSelected

    private[this] val indicesStatusMessage: String =
      s"""|<html>
          |<body>
          |This search relies on the bytecode, which is not up-to-date. <br>
          |Results may be incomplete without a compilation.
          |</body>
          |</html>
          |""".stripMargin

    setTitle(ScalaBundle.message("find.usages.compiler.indices.dialog.title", title))
    setResizable(false)
    init()

    override def createCenterPanel(): JComponent =
      FormBuilder
        .createFormBuilder()
        .addComponent(new JLabel(indicesStatusMessage))
        .addVerticalGap(3)
        .addComponent(shouldCompileCB)
        .addVerticalGap(5)
        .addComponent(settingsLink)
        .getPanel
  }
}
