package org.jetbrains.plugins.scala.testingSupport.test.testdata

import com.intellij.execution.ExternalizablePath
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.xmlb.XmlSerializer
import org.apache.commons.lang3.StringUtils
import org.jdom.Element
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.testingSupport.TestWorkingDirectoryProvider
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.{SearchForTest, TestKind}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestRunConfigurationForm}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty

abstract class TestConfigurationData(config: AbstractTestRunConfiguration) {

  def getKind: TestKind
  def checkSuiteAndTestName(): Unit
  def getTestMap: Map[String, Set[String]]

  protected final def getModule: Module = config.getModule
  protected final def getProject: Project = config.getProject
  protected final def checkModule(): Unit = if (getModule == null) throw new RuntimeConfigurationException("Module is not specified")

  protected final def mScope(module: Module, withDependencies: Boolean): GlobalSearchScope = {
    if (withDependencies) GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    else GlobalSearchScope.moduleScope(module)
  }

  protected final def unionScope(moduleGuard: Module => Boolean, withDependencies: Boolean): GlobalSearchScope = {
    var scope: GlobalSearchScope = if (getModule != null) mScope(getModule, withDependencies) else GlobalSearchScope.EMPTY_SCOPE
    for (module <- ModuleManager.getInstance(getProject).getModules) {
      if (moduleGuard(module)) {
        scope = scope.union(mScope(module, withDependencies))
      }
    }
    scope
  }

  protected def getScope(withDependencies: Boolean): GlobalSearchScope = {
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

  def writeExternal(element: Element): Unit =
    XmlSerializer.serializeInto(this, element)

  def readExternal(element: Element): Unit  = {
    XmlSerializer.deserializeInto(this, element)
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateBool("useUiWithSbt")(useUiWithSbt = _)
      helper.migrateBool("showProgressMessages")(showProgressMessages = _)
      helper.migrateBool("useSbt")(useSbt = _)
      helper.migrateString("vmparams")(javaOptions = _)
      helper.migrateString("params")(testArgs = _)
      helper.migrateString("searchForTest")(x => searchTest = SearchForTest.parse(x))
      helper.migrateMap("envs", "envVar", envs)
      helper.migrateString("workingDirectory")(workingDirectory =_)
      helper.migrateString("jrePath")(jrePath =_)
    }
  }

  protected final def isDumb: Boolean = DumbService.isDumb(config.getProject)

  // Bean settings:
  @BeanProperty var searchTest: SearchForTest     = SearchForTest.ACCROSS_MODULE_DEPENDENCIES
  @BeanProperty var showProgressMessages: Boolean = true // TODO: there already exists a parameter in Logs tab, do we need this parameter?
  @BeanProperty var useSbt: Boolean               = false
  @BeanProperty var useUiWithSbt: Boolean         = false
  @BeanProperty var jrePath: String               = _
  @BeanProperty var testArgs: String              = ""
  @BeanProperty var javaOptions: String           = ""
  @BeanProperty var envs: java.util.Map[String, String] = new java.util.HashMap[String, String]()

  private var workingDirectory: String     = ""
  def setWorkingDirectory(s: String): Unit = workingDirectory = ExternalizablePath.urlValue(s)
  def getWorkingDirectory: String          = ExternalizablePath.localPathValue(workingDirectory)

  def initWorkingDir(): Unit = {
    if (StringUtils.isBlank(workingDirectory)) {
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
}

object TestConfigurationData {

  def createFromExternal(element: Element, configuration: AbstractTestRunConfiguration): TestConfigurationData = {
    val testData = create(configuration.testKind, configuration)
    testData.readExternal(element)
    testData
  }

  def createFromForm(form: TestRunConfigurationForm, configuration: AbstractTestRunConfiguration): TestConfigurationData = {
    val testData = create(form.getSelectedKind, configuration)
    testData.apply(form)
    testData
  }

  private def create(testKind: TestKind, configuration: AbstractTestRunConfiguration) = testKind match {
    case TestKind.ALL_IN_PACKAGE => new AllInPackageTestData(configuration)
    case TestKind.CLASS          => new ClassTestData(configuration)
    case TestKind.TEST_NAME      => new SingleTestData(configuration)
    case TestKind.REGEXP         => new RegexpTestData(configuration)
  }
}
