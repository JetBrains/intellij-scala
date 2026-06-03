package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext}
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.configurations.RunConfigCreationLocation._
import org.jetbrains.plugins.scala.configurations.{RunConfigCreationContext, RunConfigCreationLocation, RunConfigurationCreationOps}
import org.jetbrains.plugins.scala.extensions.inReadAction

trait IntegrationTestConfigurationCreation extends RunConfigurationCreationOps {

  //NOTE: this helper method is not that required, but they are already used in tons of places, so we can let them be here...
  protected final def packageLoc(packageName: String): PackageLocation = PackageLocation(packageName)
  protected final def moduleLoc(moduleName: String): ModuleLocation = ModuleLocation(moduleName)
  protected final def loc(fileName: String, line: Int, column: Int): CaretLocation = CaretLocation(fileName, line, column)

  def getModule: Module
  private def getProject = getModule.getProject

  // Leaving this extra special method just because we already have >50 existing tests that use it
  protected final def createTestCaretLocation(line: Int, column: Int, fileName: String): RunnerAndConfigurationSettings =
    createTestFromLocation(loc(fileName, line, column))

  protected final def createTestFromLocation(creationLocation: RunConfigCreationLocation): RunnerAndConfigurationSettings =
    createTestFromLocation(RunConfigCreationContext(creationLocation))

  protected final def createTestFromLocation(creationContext: RunConfigCreationContext): RunnerAndConfigurationSettings = inReadAction {
    creationContext.location match {
      case loc: CaretLocation =>
        // Assume a single source root so far
        val sourceRoot = roots.ModuleRootManager.getInstance(getModule).getSourceRoots.head
        val psiElement = findPsiElement(loc, getProject, sourceRoot.toNioPath)
        createTestFromPsiElement(psiElement, creationContext)
      case loc: PackageLocation =>
        val psiDirectory = findPackageSingleDirectory(getProject, loc.packageName)
        createTestFromPsiElement(psiDirectory, creationContext)
      case loc: ModuleLocation =>
        val psiDirectory = findModuleContentRootEnsureCreated(getProject, loc.moduleName)
        createTestFromPsiElement(psiDirectory, creationContext)
      case loc: PsiElementLocation =>
        createTestFromPsiElement(loc.psiElement, creationContext)
      case _ => ???
    }
  }

  protected def createTestFromPsiElement(
    psiElement: PsiElement,
    configCreationContext: RunConfigCreationContext
  ): RunnerAndConfigurationSettings =
    inReadAction {
      val configurationContext: ConfigurationContext = new ConfigurationContext(psiElement)
      val configurationFromContext = selectSingleConfigurationOfExpectedTypeOrFail(configurationContext, configCreationContext)
      configurationFromContext.getConfigurationSettings
    }

  protected def selectSingleConfigurationOfExpectedTypeOrFail(
    context: ConfigurationContext,
    configCreationContext: RunConfigCreationContext
  ): ConfigurationFromContext
}