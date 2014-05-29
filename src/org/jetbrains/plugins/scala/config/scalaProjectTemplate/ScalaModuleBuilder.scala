package org.jetbrains.plugins.scala
package config.scalaProjectTemplate

import com.intellij.ide.util.projectWizard._
import com.intellij.openapi.module.{ModuleType, Module, ModifiableModuleModel}
import com.intellij.openapi.roots.{ModuleRootModificationUtil, ModuleRootManager}
import config.{ScalaDistribution, LibraryId, ScalaFacet}
import java.io.File
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import org.jetbrains.plugins.scala.config.scalaProjectTemplate.ui.{ScalaAdvancedModuleSettings, ScalaModuleSettingsUi}
import com.intellij.openapi.util.Condition
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import javax.swing.{Icon, JComponent}
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import components.{TypeAwareHighlightingApplicationState, HighlightingAdvisor}
import com.intellij.openapi.project.Project

/**
 * User: Dmitry Naydanov
 * Date: 11/7/12
 */
class ScalaModuleBuilder extends JavaModuleBuilder {
  private var scalaHome: String = null

  private var compilerLibraryId: LibraryId = null
  private var standardLibraryId: LibraryId = null
  private var isTypeAwareHighlightingEnabled = true
  
  def setScalaHome(scalaHome: String) { 
    this.scalaHome = scalaHome
  }
  
  def setCompilerLibraryId(libraryId: LibraryId) {
    this.compilerLibraryId = libraryId
  }
  
  def setStandardLibraryId(libraryId: LibraryId) {
    this.standardLibraryId = libraryId
  }
  
  def setTypeAwareHighlighting(isEnabled: Boolean) {
    isTypeAwareHighlightingEnabled = isEnabled
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    ScalaModuleBuilder.modifySettingsStep(settingsStep,
      (settings: ScalaModuleSettingsUi, advancedSettings: ScalaAdvancedModuleSettings) => {
        setScalaHome(settings.getScalaHome)

        setCompilerLibraryId(settings.getCompilerLibraryId)
        setStandardLibraryId(settings.getStandardLibraryId)
        setTypeAwareHighlighting(advancedSettings.isTypeAwareHighlightingEnabled)
    }, this)
  }

  override def getBuilderId: String = "ScalaModuleBuilderId"

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val module = super.createModule(moduleModel)
    
    if (scalaHome != null) {
      val distribution = ScalaDistribution from new File(scalaHome)
      val modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel
      
      val standardLibrary = distribution.createStandardLibrary(standardLibraryId, modifiableModel)
      (ScalaFacet createIn module) {
        facet =>
          distribution.createCompilerLibrary(compilerLibraryId, modifiableModel)
          facet.compilerLibraryId = compilerLibraryId
      }

      modifiableModel dispose()
      ModuleRootModificationUtil.addDependency(module, standardLibrary)
    } else if (compilerLibraryId != null && standardLibraryId != null) {
      val standardLibrary = LibraryTablesRegistrar.getInstance().getLibraryTable getLibraryByName standardLibraryId.name
      ModuleRootModificationUtil.addDependency(module, standardLibrary)
      
      (ScalaFacet createIn module) { _ compilerLibraryId_= compilerLibraryId }
    } else {
      (ScalaFacet createIn module) {facet => }
    }
    
    if (isTypeAwareHighlightingEnabled) {
      val typeAwareSettings = HighlightingAdvisor.getInstance(module.getProject).getState
      typeAwareSettings setSUGGEST_TYPE_AWARE_HIGHLIGHTING false
      typeAwareSettings setTYPE_AWARE_HIGHLIGHTING_ENABLED true
      
      TypeAwareHighlightingApplicationState.getInstance setSuggest true
    } else {
      TypeAwareHighlightingApplicationState.getInstance setSuggest false
    }
    
    module
  }

  override def createWizardSteps(wizardContext: WizardContext, 
                                 modulesProvider: ModulesProvider): Array[ModuleWizardStep] = Array[ModuleWizardStep]()

  override def getNodeIcon: Icon = org.jetbrains.plugins.scala.icons.Icons.SCALA_SMALL_LOGO
}

object ScalaModuleBuilder {
  def modifySettingsStep(settingsStep: SettingsStep,
                         forDataModel: (ScalaModuleSettingsUi, ScalaAdvancedModuleSettings) => Unit,
                         moduleBuilder: ModuleBuilder) = {
    val settings = new ScalaModuleSettingsUi(settingsStep.getContext.getProject)
    val advancedSettings = new ScalaAdvancedModuleSettings

    val newSdkSettingsStep = new SdkSettingsStep(settingsStep, moduleBuilder, new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }) {
      override def updateDataModel() {
        forDataModel(settings, advancedSettings)
        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk
      }
    }
    settingsStep addSettingsComponent settings.getMainPanel
    settingsStep addExpertPanel advancedSettings.getComponent

    newSdkSettingsStep
  }
}
