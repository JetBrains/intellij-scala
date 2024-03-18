package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.{Panel, Row}
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.ScalaLibraryProperties
import org.jetbrains.plugins.scala.project.template.{ScalaModuleBuilder, ScalaSDKStepLike}
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardStep

import java.nio.file.Paths
import javax.swing.{JComboBox, JComponent}

/** inspired by [[com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizard]] */
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
    builder.setModuleFilePath(FileUtil.toSystemDependentName(moduleFile.toString))

    setProjectOrModuleSdk(project, parent, builder, Option(getSdk))

    val librarySettings = libraryPanel.apply()
    builder.libraryCompositionSettings = librarySettings
    builder.packagePrefix = Option(packagePrefixTextField.getText).filter(_.nonEmpty)

    /** copied from [[com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizard.Step#setupProject]] */
    if (getAddSampleCode) {
      val isScala3 = isScala3SdkLibrary(librarySettings.getSelectedLibrary)
      val file = addScalaSampleCode(project, s"$getContentRoot/src", isScala3, builder.packagePrefix, getGenerateOnboardingTips)
      builder.openFileEditorAfterProjectOpened = Some(file)
    }

    builder.commit(project)
  }

  private def isScala3SdkLibrary(library: Library): Boolean = {
    val properties = library.asOptionOf[LibraryEx].map(_.getProperties)
    properties.exists {
      case scalaProperties: ScalaLibraryProperties => scalaProperties.languageLevel.isScala3
      case _ => false
    }
  }

  override def setupSettingsUI(panel: Panel): Unit = {
    setupJavaSdkUI(panel)
    setupScalaSdkUI(panel)
    setupPackagePrefixUI(panel)
    setupSampleCodeUI(panel)
    setupSampleCodeWithOnBoardingTipsUI(panel)
  }

  private def setupScalaSdkUI(panel: Panel): Unit = {
    panel.row(scalaSdkLabelText, (row: Row) => {
      val simplePanel = libraryPanel.getSimplePanel
      val components = simplePanel.getComponents.map(_.asInstanceOf[JComponent])
      // WORKAROUND: `row.cell(simplePanel)` adds some strange indent to the left which looks ugly
      // So we add all children (library dropdown & "create" button)
      // (I didn't find a a proper way to fix the border)
      components.foreach { component =>
        val cell = component match {
          case comboBox: JComboBox[_] =>
            Iterator.range(0, comboBox.getItemCount).map(comboBox.getModel.getElementAt)
              .find { case null => false; case editor: ExistingLibraryEditor => !isScala3SdkLibrary(editor.getLibrary) }
              .foreach(comboBox.setSelectedItem)
            //apply validation only for the combobox component with the library selection
            row.cell(comboBox).validation((() => {
              if (comboBox.getSelectedIndex == -1)
                new ValidationInfo(SbtBundle.message("scala.sdk.must.be.set"))
              else
                null
            }): DialogValidation)
          case _ =>
            row.cell(component)
        }
        cell
      }
      KUnit
    })
  }
}