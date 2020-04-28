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
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testIntegration.TestFramework
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.TestFrameworkRunnerInfo
import org.jetbrains.plugins.scala.testingSupport.test.sbt.SbtTestRunningSupport
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData, TestConfigurationData}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

abstract class AbstractTestRunConfiguration(
  project: Project,
  val configurationFactory: ConfigurationFactory,
  val name: String
) extends ModuleBasedConfiguration[RunConfigurationModule, Element](
  name,
  new RunConfigurationModule(project),
  configurationFactory
) with ScalaTestingConfiguration {

  def configurationProducer: AbstractTestConfigurationProducer[_]
  def testFramework: TestFramework

  def suitePaths: Seq[String]
  final def javaSuitePaths: ju.List[String] = suitePaths.asJava

  @BeanProperty
  var testKind: TestKind = TestKind.CLAZZ

  var testConfigurationData: TestConfigurationData = new ClassTestData(this)

  def testKind_(kind: TestKind): Unit = testKind = Option(kind).getOrElse(TestKind.CLAZZ)

  override def getTestClassPath: String = testConfigurationData match {
    case data: ClassTestData => data.testClassPath
    case _ => null
  }

  override def getTestPackagePath: String = testConfigurationData match {
    case data: AllInPackageTestData => data.testPackagePath
    case _ => null
  }

  private var generatedName: String = ""
  def setGeneratedName(name: String): Unit = generatedName = name
  override def isGeneratedName: Boolean = getName == generatedName
  override def suggestedName: String = generatedName

  // TODO: move to TestRunConfigurationForm.applyTo
  def apply(form: TestRunConfigurationForm): Unit = {
    setModule(form.getModule)
    setTestKind(form.getSelectedKind)
    testConfigurationData = TestConfigurationData.createFromForm(form, this)
    testConfigurationData.initWorkingDir()
  }

  protected[test] def getClazz(path: String, withDependencies: Boolean): PsiClass = {
    val classes = ScalaPsiManager.instance(project).getCachedClasses(getScope(withDependencies), path)
    val (objects, nonObjects) = classes.partition(_.isInstanceOf[ScObject])
    if (nonObjects.nonEmpty) nonObjects(0)
    else if (objects.nonEmpty) objects(0)
    else null
  }

  private def moduleScope(module: Module, withDependencies: Boolean): GlobalSearchScope = {
    if (withDependencies)
      GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    else {
      val sharedSourceScope =
        module.sharedSourceDependency
          .map(GlobalSearchScope.moduleScope)
          .getOrElse(GlobalSearchScope.EMPTY_SCOPE)
      sharedSourceScope.union(GlobalSearchScope.moduleScope(module))
    }
  }

  private def unionScope(moduleGuard: Module => Boolean, withDependencies: Boolean): GlobalSearchScope = {
    val scope: GlobalSearchScope = getModule match {
      case null   => GlobalSearchScope.EMPTY_SCOPE
      case module => moduleScope(module, withDependencies)
    }
    val projectModules = ModuleManager.getInstance(getProject).getModules
    projectModules.iterator
      .filter(moduleGuard)
      .foldLeft(scope) { case (res, module) =>
        res.union(moduleScope(module, withDependencies))
      }
  }

  private def getScope(withDependencies: Boolean): GlobalSearchScope =
    getModule match {
      case null   => unionScope(_ => true, withDependencies)
      case module => moduleScope(module, withDependencies)
    }

  final def getModule: Module =
    getConfigurationModule.getModule

  override def getValidModules: java.util.List[Module] =
    getProject.modulesWithScala.asJava

  override def getModules: Array[Module] =
    inReadAction {
      import SearchForTest._
      val module = getModule
      testConfigurationData.searchTest match {
        case ACCROSS_MODULE_DEPENDENCIES if module != null => module.withDependencyModules.toArray
        case IN_SINGLE_MODULE if module != null            => Array(module)
        case IN_WHOLE_PROJECT                              => getProject.modules.toArray
        case _                                             => Array()
      }
    }

  // TODO: when checking suite on validity we search inheritors here and in validation, extra work
  protected[test] def getSuiteClass: Either[RuntimeConfigurationException, PsiClass] = {
    val suiteClasses = suitePaths.map(getClazz(_, withDependencies = true)).filter(_ != null)

    if (suiteClasses.isEmpty) {
      val errorMessage = ScalaBundle.message("test.framework.is.not.specified", testFramework.getName)
      Left(exception(errorMessage))
    } else if (suiteClasses.size > 1) {
      Left(exception(ScalaBundle.message("test.run.config.multiple.suite.traits.detected.suiteclasses", suiteClasses)))
    } else {
      Right(suiteClasses.head)
    }
  }

  override def checkConfiguration(): Unit = {
    testConfigurationData.checkSuiteAndTestName match {
      case Left(ex) => throw ex
      case Right(_) =>
    }
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this)
  }

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = {
    val group: SettingsEditorGroup[AbstractTestRunConfiguration] = new SettingsEditorGroup
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"),
      new AbstractTestRunConfigurationEditor(project, this))
    JavaRunConfigurationExtensionManager.getInstance.appendEditors(thisConfiguration, group)
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel[AbstractTestRunConfiguration])
    group
  }

  private def thisConfiguration: RunConfigurationBase[_] = this

  // These special exceptions are used by the IntelliJ platform for exceptional control flow
  protected final def exception(message: String) = new RuntimeConfigurationException(message)
  protected final def error(message: String) = new RuntimeConfigurationError(message)
  protected final def executionException(message: String) = new ExecutionException(message)
  protected final def executionException(message: String, cause: Throwable) = new ExecutionException(message, cause)
  protected[test] def classNotFoundError: ExecutionException =
    executionException(ScalaBundle.message("test.run.config.test.class.not.found.gettestclasspath", getTestClassPath))

  protected[test] final def isValidSuite(clazz: PsiClass): Boolean =
    getSuiteClass.fold(_ => false, validityChecker.isValidSuite(clazz, _))

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
    XmlSerializer.serializeInto(testConfigurationData, element)
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

  private def migrate(element: Element): Unit = {
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateString("testKind")(x => testKind = TestKind.parse(x))
    }
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
}
