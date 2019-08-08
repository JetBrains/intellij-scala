package org.jetbrains.plugins.scala.testingSupport.test.testdata

import com.intellij.execution.ExternalizablePath
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.testingSupport.TestWorkingDirectoryProvider
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.{SearchForTest, TestKind}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestRunConfigurationForm}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty

abstract class TestConfigurationData(config: AbstractTestRunConfiguration) {
  protected def getModule: Module = config.getModule
  protected def getProject: Project = config.getProject
  protected def checkModule(): Unit = if (getModule == null) throw new RuntimeConfigurationException("Module is not specified")

  def mScope(module: Module, withDependencies: Boolean): GlobalSearchScope = {
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

  def apply(form: TestRunConfigurationForm): Unit = {
    setSearchTest(form.getSearchForTest)
    setJavaOptions(form.getJavaOptions)
    setTestArgs(form.getTestArgs)
    setJrePath(form.getJrePath)
    setShowProgressMessages(form.getShowProgressMessages)
    setUseSbt(form.getUseSbt)
    setUseUiWithSbt(form.getUseUiWithSbt)
    setWorkingDirectory(form.getWorkingDirectory)
    envs = form.getEnvironmentVariables
  }

  def initWorkingDir(): Unit = {
    if (workingDirectory == null || workingDirectory.trim.isEmpty) {
      val workingDir = moduleWorkingDirectory(getModule)
      setWorkingDirectory(workingDir)
    }
  }

  private def moduleWorkingDirectory(module: Module): String = {
    val provider = TestWorkingDirectoryProvider.EP_NAME.getExtensions.find(_.getWorkingDirectory(module) != null)
    provider match {
      case Some(provider) => provider.getWorkingDirectory(module)
      case _              => Option(getProject.baseDir).map(_.getPath).getOrElse("")
    }
  }

  def writeExternal(element: Element): Unit = XmlSerializer.serializeInto(this, element)

  def readExternal(element: Element): Unit  = {
    XmlSerializer.deserializeInto(this, element)
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateBool("useUiWithSbt")(useUiWithSbt = _)
      helper.migrateBool("showProgressMessages")(showProgressMessages = _)
      helper.migrateBool("useSbt")(useSbt = _)
      helper.migrateString("path")(testClassPath = _)
      helper.migrateString("vmparams")(javaOptions = _)
      helper.migrateString("params")(testArgs = _)
      helper.migrateString("searchForTest")(x => searchTest = SearchForTest.parse(x))
      helper.migrateMap("envs", "envVar", envs)
    }
  }

  def checkSuiteAndTestName(): Unit
  def getTestMap: Map[String, Set[String]]
  def getKind: TestKind
  def isDumb: Boolean = DumbService.isDumb(config.getProject)

  // Bean settings:

  @BeanProperty var searchTest: SearchForTest     = SearchForTest.ACCROSS_MODULE_DEPENDENCIES
  @BeanProperty var showProgressMessages: Boolean = true
  @BeanProperty var useSbt: Boolean               = false
  @BeanProperty var useUiWithSbt: Boolean         = false
  @BeanProperty var jrePath: String               = _
  @BeanProperty var testArgs: String              = ""
  @BeanProperty var javaOptions: String           = ""
  @BeanProperty var testPackagePath: String       = ""
  @BeanProperty var testClassPath: String         = ""
  @BeanProperty var envs: java.util.Map[String, String] = new java.util.HashMap[String, String]()

  private var workingDirectory: String     = ""
  def setWorkingDirectory(s: String): Unit = workingDirectory = ExternalizablePath.urlValue(s)
  def getWorkingDirectory: String          = ExternalizablePath.localPathValue(workingDirectory)
}

object TestConfigurationData {
  def createFromExternal(element: Element, configuration: AbstractTestRunConfiguration): TestConfigurationData = {
    val testData = configuration.testKind match {
      case TestKind.CLASS           => new ClassTestData(configuration)
      case TestKind.ALL_IN_PACKAGE  => new AllInPackageTestData(configuration)
      case TestKind.REGEXP          => new RegexpTestData(configuration)
      case TestKind.TEST_NAME       => new SingleTestData(configuration)
    }
    testData.readExternal(element)
    testData
  }

  def createFromForm(form: TestRunConfigurationForm, configuration: AbstractTestRunConfiguration): TestConfigurationData = {
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
