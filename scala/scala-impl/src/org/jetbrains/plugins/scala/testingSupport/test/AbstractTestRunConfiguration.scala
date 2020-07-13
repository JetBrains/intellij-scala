package org.jetbrains.plugins.scala
package testingSupport.test

import java.{util => ju}

import com.intellij.diagnostic.logging.LogConfigurationPanel
import com.intellij.execution._
import com.intellij.execution.configurations._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.options.{SettingsEditor, SettingsEditorGroup}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.{GlobalSearchScope, GlobalSearchScopes}
import com.intellij.testIntegration.TestFramework
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Transient
import org.jdom.Element
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration._
import org.jetbrains.plugins.scala.testingSupport.test.sbt.SbtTestRunningSupport
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{ClassTestData, TestConfigurationData}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

//noinspection ConvertNullInitializerToUnderscore
abstract class AbstractTestRunConfiguration(
  project: Project,
  val configurationFactory: ConfigurationFactory,
  val name: String
) extends ModuleBasedConfiguration[RunConfigurationModule, Element](
  name,
  new RunConfigurationModule(project),
  configurationFactory
) with ConfigurationWithCommandLineShortener
  with exceptions {

  def configurationProducer: AbstractTestConfigurationProducer[_]
  def testFramework: TestFramework

  def suitePaths: Seq[String]
  final def javaSuitePaths: ju.List[String] = suitePaths.asJava

  // TODO: move to test data itself to avoid information duplication (currently same info is expressed via testKind and testConfigurationData.class
  @BeanProperty
  var testKind: TestKind = TestKind.CLAZZ

  var testConfigurationData: TestConfigurationData = new ClassTestData(this)

  private var generatedName: String = ""
  def setGeneratedName(name: String): Unit = generatedName = name
  override def isGeneratedName: Boolean = getName == generatedName
  override def suggestedName: String = generatedName

  @Transient
  override def getShortenCommandLine: ShortenCommandLine = testConfigurationData.shortenClasspath
  override def setShortenCommandLine(mode: ShortenCommandLine): Unit = testConfigurationData.shortenClasspath = mode

  final def getModule: Module =
    getConfigurationModule.getModule

  override def getValidModules: java.util.List[Module] =
    getProject.modulesWithScala.asJava

  override final def getModules: Array[Module] =
    modulesToBuildBeforeRun

  def initFrom(from: AbstractTestRunConfiguration): Unit = {
    this.setGeneratedName(from.suggestedName)
    this.setFileOutputPath(from.getOutputFilePath)
    this.setModule(from.getModule)
    this.setName(from.getName)
    this.setNameChangedByUser(!from.isGeneratedName)
    this.setSaveOutputToFile(from.isSaveOutputToFile)
    this.setShowConsoleOnStdErr(from.isShowConsoleOnStdErr)
    this.setShowConsoleOnStdOut(from.isShowConsoleOnStdOut)
    this.testConfigurationData = from.testConfigurationData.copy(this)
    this.testKind = from.testKind
  }

  /**
   * dependencies are added to scope automatically via [[GlobalSearchScopes.executionScope]]
   * @see [[com.intellij.execution.configurations.CommandLineState]]
   * @see [[getSearchScope]]
   */
  private def modulesToBuildBeforeRun: Array[Module] =
    if (testConfigurationData.searchTestsInWholeProject)
      Array.empty // empty array means for full project
    else
      super.getModules

  override def getSearchScope: GlobalSearchScope = {
    val superScope = Option(super.getSearchScope)
    val notBuildModulesScope = GlobalSearchScope.notScope(buildModulesScope(getProject))
    superScope.foldLeft(notBuildModulesScope)(_.union(_))
  }

  private def buildModulesScope(project: Project): GlobalSearchScope = {
    val buildModules = project.modules.filter(_.isBuildModule)
    if (buildModules.nonEmpty)
      GlobalSearchScope.union(buildModules.map(GlobalSearchScope.moduleScope).asJavaCollection)
    else
      GlobalSearchScope.EMPTY_SCOPE
  }

  private def configurationScope: GlobalSearchScope =
    getModule match {
      case null   => projectScope(getProject)
      case module => moduleScope(module)
    }

  protected[test] def getClazz(path: String): PsiClass = {
    val scope = configurationScope
    val classes = ScalaPsiManager.instance(project).getCachedClasses(scope, path)
    val (objects, nonObjects) = classes.partition(_.isInstanceOf[ScObject])
    if (nonObjects.nonEmpty) nonObjects(0)
    else if (objects.nonEmpty) objects(0)
    else null
  }

  // TODO: when checking suite on validity we search inheritors here and in validation, extra work
  protected[test] def getSuiteClass: Either[RuntimeConfigurationException, PsiClass] = {
    val suiteClasses = suitePaths.map(getClazz).filter(_ != null)

    if (suiteClasses.isEmpty)
      Left(configurationException(ScalaBundle.message("test.framework.is.not.specified", testFramework.getName)))
    else if (suiteClasses.size > 1)
      Left(configurationException(ScalaBundle.message("test.run.config.multiple.suite.traits.detected", suiteClasses)))
    else
      Right(suiteClasses.head)
  }

  override def checkConfiguration(): Unit = {
    testConfigurationData.checkSuiteAndTestName match {
      case Left(ex) => throw ex
      case Right(_) =>
    }
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(thisConfiguration)
  }

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = {
    val group: SettingsEditorGroup[AbstractTestRunConfiguration] = new SettingsEditorGroup
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new AbstractTestRunConfigurationEditor(project))
    JavaRunConfigurationExtensionManager.getInstance.appendEditors(thisConfiguration, group)
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel[AbstractTestRunConfiguration])
    group
  }

  private def thisConfiguration: RunConfigurationBase[_] = this

  protected[test] final def isValidSuite(clazz: PsiClass): Boolean =
    getSuiteClass.fold(_ => false, validityChecker.isValidSuite(clazz, _))

  /** @return whether `clazz` can be discovered when run indirectly, e.g. using "All in package" test kind */
  protected[test] def canBeDiscovered(clazz: PsiClass): Boolean = true

  protected[test] final def isInvalidSuite(clazz: PsiClass): Boolean =
    !isValidSuite(clazz)

  protected def validityChecker: SuiteValidityChecker

  protected def runnerInfo: TestFrameworkRunnerInfo

  def sbtSupport: SbtTestRunningSupport

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val module = getModule
    if (module == null) throw executionException(ScalaBundle.message("test.run.config.module.is.not.specified"))

    new ScalaTestFrameworkCommandLineState(this, env, testConfigurationData, runnerInfo, sbtSupport)(project, module)
  }

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.writeExternal(thisConfiguration, element)
    XmlSerializer.serializeInto(this, element)
    testConfigurationData.writeExternal(element)
    PathMacroManager.getInstance(getProject).collapsePathsRecursively(element)
  }

  override def readExternal(element: Element): Unit = {
    PathMacroManager.getInstance(getProject).expandPaths(element)
    super.readExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.readExternal(thisConfiguration, element)
    readModule(element)
    XmlSerializer.deserializeInto(this, element)
    migrate(element)
    testConfigurationData = TestConfigurationData.createFromExternal(element, this)
  }

  private def migrate(element: Element): Unit =
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateString("testKind")(x => testKind = TestKind.parse(x))
    }
}

object AbstractTestRunConfiguration {

  case class SettingEntry(settingName: String, task: Option[String], sbtProjectUri: Option[String], sbtProjectId: Option[String])

  type SettingMap = Map[SettingEntry, String]

  def SettingMap(): SettingMap = Map[SettingEntry, String]()

  private[test] trait TestCommandLinePatcher {
    private var failedTests: Seq[(String, String)] = Seq()

    def setFailedTests(failedTests: Seq[(String, String)]): Unit =
      this.failedTests = Option(failedTests).map(_.distinct).orNull

    def getFailedTests: Seq[(String, String)] = failedTests

    def getClasses: Seq[String]

    @BeanProperty
    var configuration: RunConfigurationBase[_] = _
  }

  private[test] trait PropertiesExtension {
    self: SMTRunnerConsoleProperties =>

    def getRunConfigurationBase: RunConfigurationBase[_]
  }

  /**
   * see runners module for details
   * @param runnerClass fully qualified name of runner class
   */
  case class TestFrameworkRunnerInfo(runnerClass: String)

  private def moduleScope(module: Module): GlobalSearchScope =
    GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)

  /**
   * TODO: why not using just [[GlobalSearchScope.projectScope]]?
   */
  private def projectScope(project: Project): GlobalSearchScope = {
    val projectModules = ModuleManager.getInstance(project).getModules
    projectModules.iterator
      .map(moduleScope)
      .foldLeft(GlobalSearchScope.EMPTY_SCOPE)(_.union(_))
  }
}
