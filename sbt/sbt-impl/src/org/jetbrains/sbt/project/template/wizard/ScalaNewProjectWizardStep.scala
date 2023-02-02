package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.INSTANCE.{logBuildSystemChanged, logBuildSystemFinished}
import com.intellij.ide.wizard._
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.{Row, SegmentedButton}
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizard

/** analog of [[com.intellij.ide.projectWizard.generators.JavaNewProjectWizard.Step]] */
//noinspection ApiStatus,UnstableApiUsage
final class ScalaNewProjectWizardStep(parent: NewProjectWizardLanguageStep)
  extends AbstractNewProjectWizardMultiStep[ScalaNewProjectWizardStep, BuildSystemScalaNewProjectWizard](parent, BuildSystemScalaNewProjectWizard.EP_NAME)
    with BuildSystemNewProjectWizardData
    with LanguageNewProjectWizardData
    with NewProjectWizardBaseData {

  override protected def getSelf: ScalaNewProjectWizardStep = this

  override def getLabel: String = JavaUiBundle.message("label.project.wizard.new.project.build.system")

  override def setupProject(project: Project): Unit = {
    super.setupProject(project)

    //logging by analogy with com.intellij.ide.projectWizard.generators.JavaNewProjectWizard.Step.setupProject
    logBuildSystemFinished(this: NewProjectWizardStep)
  }

  override def createAndSetupSwitcher(builder: Row): SegmentedButton[String] = {
    //logging by analogy with
    //com.intellij.ide.projectWizard.generators.JavaNewProjectWizard.Step.createAndSetupSwitcher
    super.createAndSetupSwitcher(builder)
      .whenItemSelectedFromUi(null, _ => {
        logBuildSystemChanged(this: NewProjectWizardStep)
        kotlin.Unit.INSTANCE
      })
  }

  //////////////////////////////////////////
  // [BOILERPLATE] Delegate to parent
  //////////////////////////////////////////

  //BuildSystemNewProjectWizardData
  override def getBuildSystem: String = getStep
  override def getBuildSystemProperty: GraphProperty[String] = getStepProperty
  override def setBuildSystem(buildSystem: String): Unit = setStep(buildSystem)

  //LanguageNewProjectWizardData
  override def getLanguage: String = parent.getLanguage
  override def getLanguageProperty: GraphProperty[String] = parent.getLanguageProperty
  override def setLanguage(language: String): Unit = parent.setLanguage(language)

  //NewProjectWizardBaseData
  override def getNameProperty: GraphProperty[String] = parent.getNameProperty
  override def getPathProperty: GraphProperty[String] = parent.getPathProperty

  override def setName(name: String): Unit = parent.setName(name)
  override def getName: String = parent.getName

  override def getPath: String = parent.getPath
  override def setPath(path: String): Unit = parent.setPath(path)
}