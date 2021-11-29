package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.{Panel, Row, RowLayout}
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.plugins.scala.project.template.{ScalaModuleBuilder, ScalaSDKStepLike}
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardStep

import java.nio.file.Paths
import javax.swing.JComponent

final class IntelliJScalaNewProjectWizardStep(parent: ScalaNewProjectWizardStep)
  extends IntelliJNewProjectWizardStep[ScalaNewProjectWizardStep](parent)
    with ScalaSDKStepLike {

  override protected val librariesContainer: LibrariesContainer =
    LibrariesContainerFactory.createContainer(parent.getContext.getProject)

  override def setupProject(project: Project): Unit = {
    val moduleFile = Paths.get(getModuleFileLocation, getModuleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

    val builder = new ScalaModuleBuilder()
    builder.setName(getModuleName)
    builder.setContentEntryPath(FileUtil.toSystemDependentName(getContentRoot))
    builder.setModuleJdk(getSdk)
    builder.setModuleFilePath(FileUtil.toSystemDependentName(moduleFile.toString))

    builder.libraryCompositionSettings = libraryPanel.apply()
    builder.packagePrefix = Option(packagePrefixTextField.getText).filter(_.nonEmpty)

    builder.commit(project)
  }

  //noinspection ApiStatus,UnstableApiUsage
  override def customOptions(panel: Panel): Unit = {
    panel.row(scalaSdkLabelText, (row: Row) => {
      val simplePanel = libraryPanel.getSimplePanel
      val components = simplePanel.getComponents.map(_.asInstanceOf[JComponent])
      // WORKAROUND: `row.cell(simplePanel)` adds some strange indent to the left which looks ugly
      // So we add all children (library dropdown & "create" button)
      // (I didn't find a a proper way to fix the border)
      components.foreach(row.cell(_))
      KUnit
    })

    panel.row(packagePrefixLabel, (row: Row) => {
      row.cell(packagePrefixTextField).horizontalAlign(HorizontalAlign.FILL)
      row.layout(RowLayout.INDEPENDENT)
      KUnit
    })
  }
}