package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.psi.search.GlobalSearchScope
import org.jdom.Element
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.{SearchForTest, TestKind}

import scala.beans.BeanProperty

abstract class TestConfigurationData(val config: AbstractTestRunConfiguration) {
  protected def getModule = config.getModule
  protected def getProject = config.getProject
  protected def checkModule(): Unit = if (getModule == null) throw new RuntimeConfigurationException("Module is not specified")

  def mScope(module: Module, withDependencies: Boolean) = {
    if (withDependencies) GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    else GlobalSearchScope.moduleScope(module)
  }

  def unionScope(moduleGuard: Module => Boolean, withDependencies: Boolean): GlobalSearchScope = {
    var scope: GlobalSearchScope = if (getModule != null) mScope(getModule, withDependencies) else GlobalSearchScope.EMPTY_SCOPE
    for (module <- ModuleManager.getInstance(getProject).getModules) {
      if (moduleGuard(module)) {
        scope = scope.union(mScope(module, withDependencies))
      }
    }
    scope
  }

  def getScope(withDependencies: Boolean): GlobalSearchScope = {
    if (getModule != null) mScope(getModule, withDependencies)
    else unionScope(_ => true, withDependencies)
  }

  @BeanProperty
  var searchTest: SearchForTest = SearchForTest.ACCROSS_MODULE_DEPENDENCIES

  def checkSuiteAndTestName(): Unit
  @BeanProperty
  var testPackagePath = ""
  @BeanProperty
  var testClassPath = ""
  def getTestMap(): Map[String, Set[String]]
  def readExternal(element: Element): Unit
  def writeExternal(element: Element): Unit
  def getKind: TestKind
  def apply(form: TestRunConfigurationForm): Unit
  def isDumb: Boolean = DumbService.isDumb(config.getProject)
}

object TestConfigurationData {
  private val testKindId = "testKind"
  def readExternal(element: Element, configuration: AbstractTestRunConfiguration): TestConfigurationData = {
    val testKind = TestKind.fromString(Option(JDOMExternalizer.readString(element, testKindId)).getOrElse("Class"))
    val testData = testKind match {
      case TestKind.CLASS => new ClassTestData(configuration)
      case TestKind.ALL_IN_PACKAGE => new AllInPackageTestData(configuration)
      case TestKind.REGEXP => new RegexpTestData(configuration)
      case TestKind.TEST_NAME => new SingleTestData(configuration)
    }
    testData.readExternal(element)
    testData
  }

  def writeExternal(element: Element, data: TestConfigurationData): Unit = {
    JDOMExternalizer.write(element, testKindId, data.getKind.toString)
    data.writeExternal(element)
  }

  def fromForm(form: TestRunConfigurationForm, configuration: AbstractTestRunConfiguration): TestConfigurationData = {
    val testData = form.getSelectedKind match {
      case TestKind.CLASS => new ClassTestData(configuration)
      case TestKind.ALL_IN_PACKAGE => new AllInPackageTestData(configuration)
      case TestKind.REGEXP => new RegexpTestData(configuration)
      case TestKind.TEST_NAME => new SingleTestData(configuration)
    }
    testData.apply(form)
    testData
  }
}
