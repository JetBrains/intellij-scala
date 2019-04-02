package org.jetbrains.plugins.scala.findUsages.compilerReferences.search

import java.awt.FlowLayout
import java.awt.event.ActionEvent

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.FormBuilder
import javax.swing._
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

    private[this] val shouldCompileCB = {
      val checkBox = new JBCheckBox(
        "Pre-compile modules in use scope before searching",
        !CompilerIndicesSbtSettings().useManualConfiguration)
      checkBox.setMnemonic('P')
      checkBox
    }

    def shouldCompile: Boolean = shouldCompileCB.isSelected

    setTitle(ScalaBundle.message("find.usages.compiler.indices.dialog.title", title))
    setResizable(false)
    init()

    override def createCenterPanel(): JComponent = {
      val firstLine = {
        val settingsLink = {
          val link = new HyperlinkLabel("bytecode")
          link.setToolTipText("Settings | Bytecode Indices")
          link.addHyperlinkListener(_ => ShowSettingsUtil.getInstance().showSettingsDialog(element.getProject, classOf[CompilerIndicesConfigurable]))

          val linkPanel = new JPanel()
          linkPanel.setLayout(new BoxLayout(linkPanel, BoxLayout.Y_AXIS))
          linkPanel.add(Box.createVerticalStrut(2))
          linkPanel.add(link)

          linkPanel
        }

        val line = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0))
        line.add(new JLabel("This search relies on the "))
        line.add(settingsLink)
        line.add(new JLabel(", which is not up-to-date."))
        line
      }

      FormBuilder
        .createFormBuilder()
        .addComponent(firstLine)
        .addVerticalGap(1)
        .addComponent(new JLabel("Results may be incomplete without a compilation."))
        .addVerticalGap(1)
        .addComponent(shouldCompileCB)
        .getPanel
    }

    override def getPreferredFocusedComponent: JComponent = shouldCompileCB
  }
}
