package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.INSTANCE.{logBuildSystemChanged, logBuildSystemFinished}
import com.intellij.ide.wizard._
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizard

import java.nio.file.Path

/** analog of [[com.intellij.ide.projectWizard.generators.JavaNewProjectWizard.Step]] */
//noinspection ApiStatus,UnstableApiUsage
final class ScalaNewProjectWizardStep(parent: NewProjectWizardLanguageStep)
  extends AbstractNewProjectWizardMultiStep[ScalaNewProjectWizardStep, BuildSystemScalaNewProjectWizard](parent, BuildSystemScalaNewProjectWizard.EP_NAME)
    with BuildSystemNewProjectWizardData
    with LanguageNewProjectWizardData
    with NewProjectWizardBaseData {

  override protected def getSelf: ScalaNewProjectWizardStep = this

  override def getLabel: String = JavaUiBundle.message("label.project.wizard.new.project.build.system")

  locally {
    //logging by analogy with com.intellij.ide.projectWizard.generators.JavaNewProjectWizard.Step.<init>
    getBuildSystemProperty.afterChange(_ => {
      logBuildSystemChanged(this)
      kotlin.Unit.INSTANCE
    })
  }

  override def setupProject(project: Project): Unit = {
    super.setupProject(project)

    //logging by analogy with com.intellij.ide.projectWizard.generators.JavaNewProjectWizard.Step.setupProject
    logBuildSystemFinished(this)
  }

  //////////////////////////////////////////
  // [BOILERPLATE] Delegate to parent
  //////////////////////////////////////////

  //NewProjectWizardBuildSystemData
  override def getBuildSystem: String = getStep
  override def setBuildSystem(buildSystem: String): Unit = setStep(buildSystem)
  override def getBuildSystemProperty: GraphProperty[String] = getStepProperty

  //NewProjectWizardLanguageData
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