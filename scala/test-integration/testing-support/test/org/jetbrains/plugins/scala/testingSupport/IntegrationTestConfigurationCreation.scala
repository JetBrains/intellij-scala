package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.configurations.TestLocation._
import org.jetbrains.plugins.scala.configurations.{RunConfigurationCreationOps, TestLocation}

trait IntegrationTestConfigurationCreation extends RunConfigurationCreationOps {

  //NOTE: this helper methods are not actually that required, we could remove them
  protected final def packageLoc(packageName: String): PackageLocation = PackageLocation(packageName)
  protected final def moduleLoc(moduleName: String): ModuleLocation = ModuleLocation(moduleName)
  protected final def loc(fileName: String, line: Int, column: Int): CaretLocation = CaretLocation(fileName, line, column)

  protected def createTestFromCaretLocation(caretLocation: CaretLocation): RunnerAndConfigurationSettings
  protected def createTestFromPackage(packageName: String): RunnerAndConfigurationSettings
  protected def createTestFromModule(moduleName: String): RunnerAndConfigurationSettings
  protected def createTestFromPsiElement(psiElement: PsiElement): RunnerAndConfigurationSettings

  protected final def createTestCaretLocation(line: Int, column: Int, fileName: String): RunnerAndConfigurationSettings =
    createTestFromLocation(loc(fileName, line, column))
  protected final def createTestFromPackage(packageLocation: PackageLocation): RunnerAndConfigurationSettings =
    createTestFromPackage(packageLocation.packageName)
  protected final def createTestFromModule(moduleLocation: ModuleLocation): RunnerAndConfigurationSettings =
    createTestFromModule(moduleLocation.moduleName)

  protected final def createTestFromLocation(testLocation: TestLocation): RunnerAndConfigurationSettings =
    testLocation match {
      case loc: CaretLocation => createTestFromCaretLocation(loc)
      case loc: PackageLocation => createTestFromPackage(loc)
      case loc: ModuleLocation => createTestFromModule(loc)
      case loc: PsiElementLocation => createTestFromPsiElement(loc.psiElement)
      case _ => ???
    }
}