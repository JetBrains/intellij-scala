package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardStep
import com.intellij.ide.wizard.BuildSystemNewProjectWizardData
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.{Panel, Row, RowLayout}
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.ScalaLibraryProperties
import org.jetbrains.plugins.scala.project.template.{ScalaModuleBuilder, ScalaSDKStepLike}
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardStep

import java.nio.file.{Path, Paths}
import javax.swing.{JComboBox, JComponent}

/** inspired by [[com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizard]] */
final class IntelliJScalaNewProjectWizardStep(parent: ScalaNewProjectWizardStep)
  extends IntelliJNewProjectWizardStep[ScalaNewProjectWizardStep](parent)
    with BuildSystemNewProjectWizardData
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
      val file = addScalaSampleCode(project, s"$getContentRoot/src", isScala3)
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

  //noinspection ApiStatus,UnstableApiUsage
  override def customOptions(panel: Panel): Unit = {
    panel.row(scalaSdkLabelText, (row: Row) => {
      val simplePanel = libraryPanel.getSimplePanel
      val components = simplePanel.getComponents.map(_.asInstanceOf[JComponent])
      // WORKAROUND: `row.cell(simplePanel)` adds some strange indent to the left which looks ugly
      // So we add all children (library dropdown & "create" button)
      // (I didn't find a a proper way to fix the border)
      components.foreach { component =>
        val cell = component match {
          case comboBox: JComboBox[_] =>
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

  override def customAdditionalOptions(panel: Panel): Unit = {
    //TODO: (minor) align label and text field with other options in the "Advanced settings" (requires patching IntelliJ sources)
    panel.row(packagePrefixLabel, (row: Row) => {
      row.cell(packagePrefixTextField).horizontalAlign(HorizontalAlign.FILL)
      row.layout(RowLayout.INDEPENDENT)
      KUnit
    })
  }


  //////////////////////////////////////////
  // [BOILERPLATE] Delegate to parent
  //////////////////////////////////////////

  //BuildSystemNewProjectWizardData
  override def getBuildSystem: String = parent.getBuildSystem
  override def getBuildSystemProperty: GraphProperty[String] = parent.getBuildSystemProperty
  override def setBuildSystem(buildSystem: String): Unit = parent.setBuildSystem(buildSystem)

  //LanguageNewProjectWizardData
  override def getLanguage: String = parent.getLanguage
  override def setLanguage(language: String): Unit = parent.setLanguage(language)
  override def getLanguageProperty: GraphProperty[String] = parent.getLanguageProperty

  //NewProjectWizardBaseData
  override def getNameProperty: GraphProperty[String] = parent.getNameProperty
  override def getPathProperty: GraphProperty[String] = parent.getPathProperty

  override def setName(name: String): Unit = parent.setName(name)
  override def getName: String = parent.getName

  override def getPath: String = parent.getPath
  override def setPath(path: String): Unit = parent.setPath(path)

  override def getProjectPath: Path = parent.getProjectPath
}