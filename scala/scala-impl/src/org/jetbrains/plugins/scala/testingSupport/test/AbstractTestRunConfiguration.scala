package org.jetbrains.plugins.scala
package testingSupport.test

import java.io.{File, FileOutputStream, IOException, PrintStream}
import java.{util => ju}

import com.intellij.diagnostic.logging.LogConfigurationPanel
import com.intellij.execution._
import com.intellij.execution.configurations._
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandler}
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testDiscovery.JavaAutoRunManager
import com.intellij.execution.testframework.autotest.{AbstractAutoTestManager, ToggleAutoTestAction}
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.options.{SettingsEditor, SettingsEditorGroup}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JdkUtil, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testIntegration.TestFramework
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingConfiguration
import org.jetbrains.plugins.scala.testingSupport.locationProvider.ScalaTestLocationProvider
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration._
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.{SearchForTest, TestKind}
import org.jetbrains.plugins.scala.testingSupport.test.actions.ScalaRerunFailedTestsAction
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{ReportingSbtTestEventHandler, SbtProcessHandlerWrapper, SbtShellTestsRunner, SbtTestRunningSupport}
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData, TestConfigurationData}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper
import org.jetbrains.sbt.shell.SbtProcessManager

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.collection.mutable

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

  private def expandPath(text: String): String = {
    var result = text
    result = PathMacroManager.getInstance(project).expandPath(result)
    val module = getModule
    if (module != null)
      result = PathMacroManager.getInstance(getModule).expandPath(result)
    result
  }

  private def expandEnvs(text: String, envs: scala.collection.Map[String, String]) =
    envs.foldLeft(text) { case (text, (key, value)) =>
      StringUtil.replace(text, "$" + key + "$", value, false)
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

  protected def sbtSupport: SbtTestRunningSupport

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val module = getModule
    if (module == null) throw executionException(ScalaBundle.message("test.run.config.module.is.not.specified"))

    //noinspection TypeAnnotation
    val state = new JavaCommandLineState(env) with AbstractTestRunConfiguration.TestCommandLinePatcher {
      private def suitesToTestsMap: Map[String, Set[String]] = {
        val failedTests = getFailedTests.groupBy(_._1).map { case (aClass, tests) => (aClass, tests.map(_._2).toSet) }.filter(_._2.nonEmpty)
        if (failedTests.nonEmpty) {
          failedTests
        } else {
          testConfigurationData.getTestMap
        }
      }

      override val getClasses: Seq[String] = testConfigurationData.getTestMap.keys.toSeq

      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters()

        params.setCharset(null)

        val envs = new ju.HashMap[String, String](testConfigurationData.envs)
        params.setEnv(envs)

        val vmParams = {
          val params0 = testConfigurationData.getJavaOptions
          val params1 = expandPath(params0)
          expandEnvs(params1, envs.asScala)
        }
        params.getVMParametersList.addParametersString(vmParams)

        // UNCOMMENT TO DEBUG:
        //params.getVMParametersList.addParametersString("-agentlib:jdwp=transport=dt_socket,server=n,address=UNIT-549.Labs.IntelliJ.Net:5005,suspend=y")

        val workingDir = testConfigurationData.getWorkingDirectory
        params.setWorkingDirectory(expandPath(workingDir))

        params.getClassPath.addRunners()
        params.setMainClass(runnerInfo.runnerClass)

        // hack fix for SCL-12564
        ManagingFS.getInstance match {
          case fs: PersistentFSImpl => fs.incStructuralModificationCount()
          case _                    =>
        }

        val maybeCustomSdk = for {
          path <- testConfigurationData.jrePath.toOption
          jdk  <- ProjectJdkTable.getInstance().findJdk(path).toOption
        } yield jdk

        testConfigurationData.searchTest match {
          case SearchForTest.IN_WHOLE_PROJECT =>
            def anyJdk: Option[Sdk] = {
              val modules = project.modules.iterator
              modules.map(JavaParameters.getValidJdkToRunModule(_, false)).headOption
            }
            val sdk = maybeCustomSdk.orElse(anyJdk)
            params.configureByProject(project, JavaParameters.JDK_AND_CLASSES_AND_TESTS, sdk.orNull)
          case _ =>
            val sdk = maybeCustomSdk.getOrElse(JavaParameters.getValidJdkToRunModule(module, false))
            params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS, sdk)
        }

        val programParameters = buildProgramParameters(suitesToTestsMap)
        if (JdkUtil.useDynamicClasspath(project)) {
          val fileWithParams = prepareDynamicClasspathFile(programParameters)
          params.getProgramParametersList.add("@" + fileWithParams.getPath)
        } else {
          programParameters.foreach(params.getProgramParametersList.add)
        }

        for (ext <- RunConfigurationExtension.EP_NAME.getExtensionList.asScala)
          ext.updateJavaParameters(thisConfiguration, params, getRunnerSettings)

        params
      }

      private def buildProgramParameters(suitesToTests: Map[String, Set[String]]): Seq[String] = {
        val classesAndTests = buildClassesAndTestsParameters(suitesToTests)
        val runner = runnerInfo.reporterClass.toSeq.flatMap(Seq("-C", _))
        val progress = Seq("-showProgressMessages", testConfigurationData.showProgressMessages.toString)
        val other = ParametersList.parse(testConfigurationData.getTestArgs)
        classesAndTests ++ runner ++ progress ++ other
      }

      private def buildClassesAndTestsParameters(suitesToTests: Map[String, Set[String]]): Seq[String] = {
        val classKey = "-s"
        val testNameKey = "-testName"

        suitesToTests.flatMap { case (className, tests) =>
          val classParams = Seq(classKey, sanitize(className))
          val testsParams = tests.filter(_ != "").flatMap(Seq(testNameKey, _))
          classParams ++ testsParams
        }.toSeq
      }

      override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
        val useSbt = testConfigurationData.useSbt
        val useUiWithSbt = testConfigurationData.useUiWithSbt

        val processHandler = if (useSbt) { // TODO: only if supported
          sbtShellProcess(project)
        } else {
          startProcess()
        }

        if (getConfiguration == null)
          setConfiguration(thisConfiguration)
        val config = getConfiguration // shouldn't it be used everywhere below instead of thisConfiguration?

        val runnerSettings = getRunnerSettings
        JavaRunConfigurationExtensionManager.getInstance
          .attachExtensionsToProcess(thisConfiguration, processHandler, runnerSettings)

        val useSimplifiedConsoleView = useSbt && !useUiWithSbt
        val consoleView = if (useSimplifiedConsoleView) {
          new ConsoleViewImpl(project, true)
        } else {
          val consoleProperties = new SMTRunnerConsoleProperties(thisConfiguration, "Scala", executor)
            with PropertiesExtension {
            override def getTestLocator = new ScalaTestLocationProvider
            override def getRunConfigurationBase: RunConfigurationBase[_] = config
          }
          consoleProperties.setIdBasedTestTree(true)
          SMTestRunnerConnectionUtil.createConsole("Scala", consoleProperties)
        }
        consoleView.attachToProcess(processHandler)

        val executionResult = createExecutionResult(consoleView, processHandler)

        // TODO
        // uncomment for debugging
        processHandler.addProcessListener(new ProcessAdapter {
          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit =
            print(s"[$outputType] ${event.getText}")
        })

        if (useSbt) {
          Stats.trigger(FeatureKey.sbtShellTestRunConfig)
          val future = SbtShellTestsRunner.runTestsInSbtShell(
            sbtSupport,
            getModule,
            suitesToTestsMap,
            new ReportingSbtTestEventHandler((message, key) => {
              processHandler.notifyTextAvailable(message, key)
            }),
            useUiWithSbt
          )
          future.onComplete(_ => processHandler.destroyProcess())(sbtSupport.executionContext)
        }

        executionResult
      }

      private def sbtShellProcess(project: Project): ProcessHandler = {
        //use a process running sbt
        val sbtProcessManager = SbtProcessManager.forProject(project)
        //make sure the process is initialized
        val shellRunner = sbtProcessManager.acquireShellRunner()
        SbtProcessHandlerWrapper(shellRunner.createProcessHandler)
      }
    }
    state
  }

  private def createExecutionResult(consoleView: ConsoleView, processHandler: ProcessHandler): DefaultExecutionResult = {
    val result = new DefaultExecutionResult(consoleView, processHandler)
    val restartActions = createRestartActions(consoleView).toSeq.flatten
    result.setRestartActions(restartActions: _*)
    result
  }

  private def createRestartActions(consoleView: ConsoleView) =
    consoleView match {
      case testConsole: BaseTestsOutputConsoleView =>
        val rerunFailedTestsAction = {
          val action = new ScalaRerunFailedTestsAction(testConsole)
          action.init(testConsole.getProperties)
          action.setModelProvider(() => testConsole.asInstanceOf[SMTRunnerConsoleView].getResultsViewer)
          action
        }
        val toggleAutoTestAction   = new ToggleAutoTestAction() {
          override def isDelayApplicable: Boolean = false
          override def getAutoTestManager(project: Project): AbstractAutoTestManager = JavaAutoRunManager.getInstance(project)
        }
        Some(Seq(rerunFailedTestsAction, toggleAutoTestAction))
      case _ =>
        None
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

  // ScalaTest does not understand backticks in class/package qualified names, it will fail to run
  private def sanitize(qualifiedName: String): String = qualifiedName.replace("`", "")

  private def prepareDynamicClasspathFile(
    programParameters: Seq[String]
  ): File = try {
    val fileWithParams: File = File.createTempFile("abstracttest", ".tmp")
    using(new PrintStream(new FileOutputStream(fileWithParams))) { printer =>
      programParameters.foreach(printer.println)
    }
    fileWithParams
  } catch {
    case e: IOException =>
      throw new ExecutionException("Failed to create dynamic classpath file with command-line args.", e)
  }
}
