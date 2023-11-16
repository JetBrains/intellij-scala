package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.diagnostic.logging.LogConfigurationPanel
import com.intellij.execution._
import com.intellij.execution.configurations._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.ConsolePropertiesProvider
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.options.{SettingsEditor, SettingsEditorGroup}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.{GlobalSearchScope, GlobalSearchScopes}
import com.intellij.util.keyFMap.KeyFMap
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Transient
import org.jdom.Element
import org.jetbrains.annotations.{ApiStatus, Nullable}
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.plugins.scala.testingSupport.TestingSupportBundle
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration._
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{ClassTestData, TestConfigurationData}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import java.{util => ju}
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._

//noinspection ConvertNullInitializerToUnderscore
abstract class AbstractTestRunConfiguration(
  project: Project,
  val configurationFactory: ConfigurationFactory,
  val name: String
) extends ModuleBasedConfiguration[JavaRunConfigurationModule, Element](
  name,
  new JavaRunConfigurationModule(project, true),
  configurationFactory
) with ConfigurationWithCommandLineShortener
  with ConsolePropertiesProvider
  with exceptions {

  @Nullable
  override def createTestConsoleProperties(executor: Executor): SMTRunnerConsoleProperties = {
    val hideTestRunnerConsole = this.testConfigurationData.useSbt && !this.testConfigurationData.useUiWithSbt
    // CWM-1987
    if (hideTestRunnerConsole) null
    else new ScalaTestFrameworkConsoleProperties(this, testFramework.getName, executor)
  }

  def configurationProducer: AbstractTestConfigurationProducer[_]
  def testFramework: AbstractTestFramework

  private lazy val suitePaths: Seq[String] = testFramework.baseSuitePaths
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
    if (getModules.isEmpty) {
      val superScope = Option(super.getSearchScope).getOrElse(GlobalSearchScope.EMPTY_SCOPE)
      val allRegularModulesScope = notBuildModulesScope(getProject)
      superScope.union(allRegularModulesScope)
    }
    else super.getSearchScope
  }

  private def notBuildModulesScope(project: Project): GlobalSearchScope = {
    val notBuildModules = project.modules.filterNot(_.isBuildModule)
    if (notBuildModules.nonEmpty)
      GlobalSearchScope.union(notBuildModules.map(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope).asJavaCollection)
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
      Left(configurationException(TestingSupportBundle.message("test.framework.is.not.specified", testFramework.getName)))
    else if (suiteClasses.size > 1)
      Left(configurationException(TestingSupportBundle.message("test.run.config.multiple.suite.traits.detected", suiteClasses)))
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

  protected[test] final def isValidSuite(clazz: PsiClass): Boolean = {
    val suiteClass = getSuiteClass
    suiteClass.fold(
      exception => {
        Log.traceSafe(s"isValidSuite: false (${exception.getMessageHtml.toString})")
        false
      },
      validityChecker.isValidSuite(clazz, _)
    )
  }

  /** @return whether `clazz` can be discovered when run indirectly, e.g. using "All in package" test kind */
  protected[test] def canBeDiscovered(clazz: PsiClass): Boolean = true

  protected[test] final def isInvalidSuite(clazz: PsiClass): Boolean =
    !isValidSuite(clazz)

  protected def validityChecker: SuiteValidityChecker

  override final def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState ={
    val provider = runStateProvider
    provider.commandLineState(env, None)
  }

  def runStateProvider: RunStateProvider

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
    //TODO: since UserDataHolderBase extends AtomicReference in 2021.1 deserialization of it's value doesn't work and plain
    // Object instance is deserialized instead of KeyFMap
    set(KeyFMap.EMPTY_MAP)
    migrate(element)
    testConfigurationData = TestConfigurationData.createFromExternal(element, this)
  }

  private def migrate(element: Element): Unit =
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateString("testKind")(x => testKind = TestKind.parse(x))
    }
}

object AbstractTestRunConfiguration {

  private val Log = Logger.getInstance(getClass)

  case class SettingEntry(settingName: String, task: Option[String], sbtProjectUri: Option[String], sbtProjectId: Option[String])

  type SettingMap = Map[SettingEntry, String]

  def SettingMap(): SettingMap = Map[SettingEntry, String]()

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
