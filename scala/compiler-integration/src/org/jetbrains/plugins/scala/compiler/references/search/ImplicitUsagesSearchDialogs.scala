package org.jetbrains.plugins.scala.compiler.references.search

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.FormBuilder
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.findUsages.UsageType
import org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceService
import org.jetbrains.plugins.scala.compiler.references.settings.CompilerIndicesConfigurable
import org.jetbrains.plugins.scala.settings.{CompilerIndicesSbtSettings, CompilerIndicesSettings}

import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing._
import scala.annotation.nowarn

private object ImplicitUsagesSearchDialogs {
  class EnableCompilerIndicesDialog(project: Project, canBeParent: Boolean, usageType: UsageType)
    extends DialogWrapper(project, canBeParent) {

    private[this] val settingsLink =
      new LinkLabel[AnyRef](CompilerIntegrationBundle.message("bytecode.indices.settings.navigate"), null) {
        setListener({
          case (_, _) =>
            close(DialogWrapper.CLOSE_EXIT_CODE)
            ShowSettingsUtil.getInstance().showSettingsDialog(project, classOf[CompilerIndicesConfigurable])
        }, null)
      }

    private[this] val description = new JLabel(
      s"""<html>${CompilerIntegrationBundle.message("bytecode.indices.must.be.enabled.1", usageType)}<br>
        ${CompilerIntegrationBundle.message("bytecode.indices.must.be.enabled.2")}</html>""".stripMargin)

    setTitle(CompilerIntegrationBundle.message("bytecode.indices.enable.indexing"))
    setResizable(false)
    init()

    override def createCenterPanel(): JComponent =
      FormBuilder.createFormBuilder()
        .addComponent(description)
        .addComponent(settingsLink)
        .getPanel

    override def createActions(): Array[Action] = {
      def enableCompilerIndices(): Unit = {
        val settings = CompilerIndicesSettings(project)
        val previous = settings.isIndexingEnabled
        if (!previous) {
          ScalaCompilerReferenceService(project).invalidateIndex()
        }
        CompilerIndicesSettings(project).setIndexingEnabled(true)
      }

      val enable = new DialogWrapperAction(CompilerIntegrationBundle.message("bytecode.indices.enable")) {
        override def doAction(e: ActionEvent): Unit = {
          enableCompilerIndices()
          close(DialogWrapper.OK_EXIT_CODE)
        }
      }

      val enableAndRestart = new DialogWrapperAction(CompilerIntegrationBundle.message("bytecode.indices.enable.and.restart")) {
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
  ) extends DialogWrapper(element.getProject, canBeParent, DialogWrapper.IdeModalityType.PROJECT: @nowarn("cat=deprecation")) {

    private[this] val shouldCompileCB = {
      val checkBox = new JBCheckBox(
        CompilerIntegrationBundle.message("bytecode.indices.precompile"),
        !CompilerIndicesSbtSettings().useManualConfiguration)
      checkBox.setMnemonic('P')
      checkBox
    }

    def shouldCompile: Boolean = shouldCompileCB.isSelected

    setTitle(CompilerIntegrationBundle.message("bytecode.indices.find.usages.with.title", title))
    setResizable(false)
    init()

    override def createCenterPanel(): JComponent = {
      val firstLine = {
        val settingsLink = {
          val link = new HyperlinkLabel(CompilerIntegrationBundle.message("bytecode.indices.bytecode"))
          link.setToolTipText(CompilerIntegrationBundle.message("bytecode.indices.settings"))
          link.addHyperlinkListener(_ => ShowSettingsUtil.getInstance().showSettingsDialog(element.getProject, classOf[CompilerIndicesConfigurable]))

          val linkPanel = new JPanel()
          linkPanel.setLayout(new BoxLayout(linkPanel, BoxLayout.Y_AXIS))
          linkPanel.add(Box.createVerticalStrut(2))
          linkPanel.add(link)

          linkPanel
        }

        val line = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0))
        line.add(new JLabel(CompilerIntegrationBundle.message("bytecode.indices.required") + " "))
        line.add(settingsLink)
        line.add(new JLabel(CompilerIntegrationBundle.message("bytecode.indices.outdated")))
        line
      }

      FormBuilder
        .createFormBuilder()
        .addComponent(firstLine)
        .addVerticalGap(1)
        .addComponent(new JLabel(CompilerIntegrationBundle.message("bytecode.indices.incomplete")))
        .addVerticalGap(1)
        .addComponent(shouldCompileCB)
        .getPanel
    }

    override def getPreferredFocusedComponent: JComponent = shouldCompileCB
  }
}
