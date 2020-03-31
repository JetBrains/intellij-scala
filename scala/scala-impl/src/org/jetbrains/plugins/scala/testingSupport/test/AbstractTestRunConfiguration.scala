package org.jetbrains.plugins.scala
package testingSupport.test

import java.io.{File, FileOutputStream, IOException, PrintStream}

import com.intellij.diagnostic.logging.LogConfigurationPanel
import com.intellij.execution._
import com.intellij.execution.configurations._
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testDiscovery.JavaAutoRunManager
import com.intellij.execution.testframework.TestFrameworkRunningModel
import com.intellij.execution.testframework.autotest.{AbstractAutoTestManager, ToggleAutoTestAction}
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.options.{SettingsEditor, SettingsEditorGroup}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JdkUtil, ProjectJdkTable}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Computable, Getter}
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope.{EMPTY_SCOPE, moduleScope}
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingConfiguration
import org.jetbrains.plugins.scala.testingSupport.locationProvider.ScalaTestLocationProvider
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.{PropertiesExtension, SettingEntry, SettingMap}
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.{SearchForTest, TestKind}
import org.jetbrains.plugins.scala.testingSupport.test.actions.AbstractTestRerunFailedTestsAction
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{SbtProcessHandlerWrapper, SbtTestEventHandler}
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData, TestConfigurationData}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SettingQueryHandler}

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class AbstractTestRunConfiguration(project: Project,
                                            val configurationFactory: ConfigurationFactory,
                                            val name: String,
                                            val configurationProducer: AbstractTestConfigurationProducer[_ <: AbstractTestRunConfiguration])
  extends ModuleBasedConfiguration[RunConfigurationModule,Element](
    name,
    new RunConfigurationModule(project),
    configurationFactory
  ) with ScalaTestingConfiguration {

  @BeanProperty var testKind: TestKind = TestKind.CLAZZ

  def testKind_(kind: TestKind): Unit = testKind = Option(testKind).getOrElse(TestKind.CLAZZ)

  var testConfigurationData: TestConfigurationData = new ClassTestData(this)

  final def javaSuitePaths: java.util.List[String] = suitePaths.asJava

  private var addIntegrationTestsClasspath: Boolean = false

  def setupIntegrationTestClassPath(): Unit = addIntegrationTestsClasspath = true

  def currentConfiguration: AbstractTestRunConfiguration = AbstractTestRunConfiguration.this

  def suitePaths: Seq[String]

  def runnerClassName: String

  def reporterClass: String

  @Nls
  def errorMessage: String

  override def getTestClassPath: String = testConfigurationData match {
    case data: ClassTestData => data.testClassPath
    case _ => null
  }

  override def getTestPackagePath: String = testConfigurationData match {
    case data: AllInPackageTestData => data.testPackagePath
    case _ => null
  }

  def allowsSbtUiRun: Boolean = false

  private var generatedName: String = ""
  def setGeneratedName(name: String): Unit = generatedName = name

  override def isGeneratedName: Boolean = getName == null || getName.equals(suggestedName)

  override def suggestedName: String = generatedName

  def apply(form: TestRunConfigurationForm): Unit = {
    setModule(form.getModule)
    setTestKind(form.getSelectedKind)
    testConfigurationData = TestConfigurationData.createFromForm(form, this)
    testConfigurationData.initWorkingDir()
  }

  protected[test] def getClazz(path: String, withDependencies: Boolean): PsiClass = {
    val classes = ScalaPsiManager.instance(project).getCachedClasses(getScope(withDependencies), path)
    val objectClasses = classes.filter(_.isInstanceOf[ScObject])
    val nonObjectClasses = classes.filter(!_.isInstanceOf[ScObject])
    if (nonObjectClasses.nonEmpty) nonObjectClasses(0) else if (objectClasses.nonEmpty) objectClasses(0) else null
  }

  def mScope(module: Module, withDependencies: Boolean): GlobalSearchScope = {
    if (withDependencies) GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    else {
      val sharedSourceScope =
        module.sharedSourceDependency
          .map(moduleScope)
          .getOrElse(EMPTY_SCOPE)

      sharedSourceScope.union(moduleScope(module))
    }
  }

  def unionScope(moduleGuard: Module => Boolean, withDependencies: Boolean): GlobalSearchScope = {
    var scope: GlobalSearchScope = getModule match {
      case null   => EMPTY_SCOPE
      case module => mScope(module, withDependencies)
    }
    val projectModules = ModuleManager.getInstance(getProject).getModules
    for (module <- projectModules) {
      if (moduleGuard(module)) {
        scope = scope.union(mScope(module, withDependencies))
      }
    }
    scope
  }

  def getScope(withDependencies: Boolean): GlobalSearchScope =
    getModule match {
      case null   => unionScope(_ => true, withDependencies)
      case module => mScope(module, withDependencies)
    }

  def expandPath(_path: String): String = {
    var path = _path
    path = PathMacroManager.getInstance(project).expandPath(path)
    if (getModule != null) {
      path = PathMacroManager.getInstance(getModule).expandPath(path)
    }
    path
  }

  final def getModule: Module =
    getConfigurationModule.getModule

  override def getValidModules: java.util.List[Module] =
    getProject.modulesWithScala.asJava

  def classKey: String = "-s"

  def testNameKey: String = "-testName"

  protected def sbtClassKey = " "

  protected def sbtTestNameKey = " "

  protected def modifySbtSettingsForUi(comm: SbtShellCommunication): Future[SettingMap] =
    Future(Map.empty)

  protected def initialize(comm: SbtShellCommunication): Future[String] =
    comm.command("initialize")

  protected def modifySetting(settings: SettingMap,
                              setting: String,
                              showTaskName: String,
                              setTaskName: String,
                              value: String,
                              comm: SbtShellCommunication,
                              modificationCondition: String => Boolean,
                              shouldSet: Boolean = false,
                              shouldRevert: Boolean = true): Future[SettingMap] = {
    val (projectUri, projectId) = getSbtProjectUriAndId
    val showHandler = SettingQueryHandler(setting, Some(showTaskName), projectUri, projectId, comm)
    val setHandler = if (showTaskName == setTaskName) showHandler else
      SettingQueryHandler(setting, Some(setTaskName), projectUri, projectId, comm)
    showHandler.getSettingValue.flatMap {
      opts =>
        (if (modificationCondition(opts))
          if (shouldSet) setHandler.setSettingValue(value) else setHandler.addToSettingValue(value)
        else Future(true)).flatMap { success =>
          //TODO check 'opts.nonEmpty()' is required so that we don't try to mess up settings if nothing was read
          if (success && shouldRevert && opts.nonEmpty && modificationCondition(opts))
            Future(settings + (SettingEntry(setting, Some(setTaskName), projectUri, projectId) -> opts))
          else if (success) Future(settings)
          else Future.failed[SettingMap](new RuntimeException("Failed to modify sbt project settings"))
          //TODO: meaningful report if settings were not set correctly
        }
    }
  }

  protected def resetSbtSettingsForUi(comm: SbtShellCommunication, oldSettings: SettingMap): Future[Boolean] = {
    Future.sequence(for ((settingEntry, value) <- oldSettings) yield {
      SettingQueryHandler(settingEntry, comm).setSettingValue(value)
    }) map {
      _.forall(identity)
    }
  }

  override def getModules: Array[Module] = {
    ApplicationManager.getApplication.runReadAction(new Computable[Array[Module]] {
      @SuppressWarnings(Array("ConstantConditions"))
      override def compute: Array[Module] = {
        testConfigurationData.searchTest match {
          case SearchForTest.ACCROSS_MODULE_DEPENDENCIES if getModule != null =>
            val buffer = new ArrayBuffer[Module]()
            buffer += getModule
            for (module <- ModuleManager.getInstance(getProject).getModules) {
              if (ModuleManager.getInstance(getProject).isModuleDependent(getModule, module)) {
                buffer += module
              }
            }
            buffer.toArray
          case SearchForTest.IN_SINGLE_MODULE if getModule != null => Array(getModule)
          case SearchForTest.IN_WHOLE_PROJECT => ModuleManager.getInstance(getProject).getModules
          case _ => Array.empty
        }
      }
    })
  }

  protected[test] def getSuiteClass: Either[RuntimeConfigurationException, PsiClass] = {
    val suiteClasses = suitePaths.map(getClazz(_, withDependencies = true)).filter(_ != null)

    if (suiteClasses.isEmpty) {
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
    JavaRunConfigurationExtensionManager.getInstance.appendEditors(this, group)
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel[AbstractTestRunConfiguration])
    group
  }

  //TODO: move logic to isValidState (including inheritors), make final, to avoid double negations
  protected[test] def isInvalidSuite(clazz: PsiClass): Boolean = getSuiteClass.fold(_ => true, AbstractTestRunConfiguration.isInvalidSuite(clazz, _))

  protected[test] final def isValidSuite(clazz: PsiClass): Boolean = !isInvalidSuite(clazz)

  private def addParametersString(pString: String, add: String => Unit): Unit = ParametersList.parse(pString).foreach(add)

  def addClassesAndTests(classToTests: Map[String, Set[String]], add: String => Unit): Unit = {
    for ((className, tests) <- classToTests) {
      add(classKey)
      add(sanitize(className))
      for (test <- tests if test != "") {
        add(testNameKey)
        add(test)
      }
    }
  }

  // ScalaTest does not understand backticks in class/package qualified names, it will fail to run
  private def sanitize(qualifiedName: String): String = qualifiedName.replace("`", "")

  protected def escapeTestName(test: String): String = {
    if (test.contains(" ")) s""""$test""""
    else test
  }

  protected def escapeClassAndTest(input: String): String = input

  def buildSbtParams(classToTests: Map[String, Set[String]]): Seq[String] = {
    (for ((aClass, tests) <- classToTests) yield {
      if (tests.isEmpty) {
        Seq(sanitize(s"$sbtClassKey$aClass"))
      } else {
        for (test <- tests) yield {
          sbtClassKey + sanitize(escapeClassAndTest(aClass + sbtTestNameKey + escapeTestName(test)))
        }
      }
    }).flatten.toSeq
  }

  protected final def exception(message: String) = new RuntimeConfigurationException(message)
  protected final def error(message: String) = new RuntimeConfigurationError(message)
  protected final def executionException(message: String) = new ExecutionException(message)

  protected[test] def classNotFoundError: ExecutionException = executionException(ScalaBundle.message("test.run.config.test.class.not.found.gettestclasspath", getTestClassPath))

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val module = getModule
    if (module == null) throw new ExecutionException(ScalaBundle.message("test.run.config.module.is.not.specified"))

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
        var vmParams = testConfigurationData.getJavaOptions

        //expand macros
        vmParams = PathMacroManager.getInstance(project).expandPath(vmParams)

        if (module != null) {
          vmParams = PathMacroManager.getInstance(module).expandPath(vmParams)
        }

        val mutableEnvVariables = testConfigurationData.envs
        params.setEnv(mutableEnvVariables)

        //expand environment variables in vmParams
        params.getEnv.entrySet.forEach { entry =>
          vmParams = StringUtil.replace(vmParams, "$" + entry.getKey + "$", entry.getValue, false)
        }

        params.getVMParametersList.addParametersString(vmParams)
        val wDir = testConfigurationData.getWorkingDirectory
        params.setWorkingDirectory(expandPath(wDir))

        //                params.getVMParametersList.addParametersString("-agentlib:jdwp=transport=dt_socket,server=n,address=UNIT-549.Labs.IntelliJ.Net:5005,suspend=y")

        params.getClassPath.addRunners()
        //a workaround to add jars for integration tests
        if (addIntegrationTestsClasspath) {
          params.getClassPath.addRunners()
        }

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
            val sdk = maybeCustomSdk.orElse(
              project
                .modules
                .iterator.map(JavaParameters.getValidJdkToRunModule(_, false))
                .collectFirst { case jdk if jdk ne null => jdk }
            )
            params.configureByProject(project, JavaParameters.JDK_AND_CLASSES_AND_TESTS, sdk.orNull)
          case _ =>
            val sdk = maybeCustomSdk.getOrElse(JavaParameters.getValidJdkToRunModule(module, false))
            params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS, sdk)
        }

        params.setMainClass(runnerClassName)

        def addParameters(add: String => Unit, after: () => Unit): Unit = {
          addClassesAndTests(suitesToTestsMap, add)
          if (reporterClass != null) {
            add("-C")
            add(reporterClass)
          }

          add("-showProgressMessages")
          add(testConfigurationData.showProgressMessages.toString)
          after()
        }

        val (myAdd, myAfter): (String => Unit, () => Unit) =
          if (JdkUtil.useDynamicClasspath(getProject)) {
            try {
              val fileWithParams: File = File.createTempFile("abstracttest", ".tmp")
              val outputStream = new FileOutputStream(fileWithParams)
              val printer: PrintStream = new PrintStream(outputStream)
              params.getProgramParametersList.add("@" + fileWithParams.getPath)

              (printer.println(_:String), () => printer.close())
            }
            catch {
              case ioException: IOException => throw new ExecutionException("Failed to create dynamic classpath file with command-line args.", ioException)
            }
          } else {
            (params.getProgramParametersList.add(_), () => ())
          }

        addParametersString(testConfigurationData.getTestArgs, myAdd)
        addParameters(myAdd, myAfter)

        for (ext <- RunConfigurationExtension.EP_NAME.getExtensionList.asScala) {
          ext.updateJavaParameters(currentConfiguration, params, getRunnerSettings)
        }

        params
      }

      override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
        val processHandler = if (testConfigurationData.useSbt) {
          //use a process running sbt
          val sbtProcessManager = SbtProcessManager.forProject(project)
          //make sure the process is initialized
          val shellRunner = sbtProcessManager.acquireShellRunner()
          //TODO: this null is really weird
          // null parameter does not affect anything, rethink API
          SbtProcessHandlerWrapper(shellRunner.createProcessHandler(null))
        } else {
          startProcess()
        }
        val runnerSettings = getRunnerSettings
        if (getConfiguration == null) setConfiguration(currentConfiguration)
        val config = getConfiguration
        JavaRunConfigurationExtensionManager.getInstance.
          attachExtensionsToProcess(currentConfiguration, processHandler, runnerSettings)
        val consoleProperties = new SMTRunnerConsoleProperties(currentConfiguration, "Scala", executor)
          with PropertiesExtension {
          override def getTestLocator = new ScalaTestLocationProvider

          override def getRunConfigurationBase: RunConfigurationBase[_] = config
        }

        consoleProperties.setIdBasedTestTree(true)

        // console view
        val consoleView = if (testConfigurationData.useSbt && !testConfigurationData.useUiWithSbt) {
          val console = new ConsoleViewImpl(project, true)
          console.attachToProcess(processHandler)
          console
        } else SMTestRunnerConnectionUtil.createAndAttachConsole("Scala", processHandler, consoleProperties)

        val res = new DefaultExecutionResult(
          consoleView, processHandler,
          createActions(consoleView, processHandler, executor): _*)

        consoleView match {
          case testConsole: BaseTestsOutputConsoleView =>
            val rerunFailedTestsAction = new AbstractTestRerunFailedTestsAction(testConsole)
            rerunFailedTestsAction.init(testConsole.getProperties)
            rerunFailedTestsAction.setModelProvider(new Getter[TestFrameworkRunningModel] {
              override def get: TestFrameworkRunningModel = {
                testConsole.asInstanceOf[SMTRunnerConsoleView].getResultsViewer
              }
            })
            res.setRestartActions(rerunFailedTestsAction, new ToggleAutoTestAction() {
              override def isDelayApplicable: Boolean = false

              override def getAutoTestManager(project: Project): AbstractAutoTestManager = JavaAutoRunManager.getInstance(project)
            })
          case _ =>
        }
        if (testConfigurationData.useSbt) {
          Stats.trigger(FeatureKey.sbtShellTestRunConfig)

          val commands = buildSbtParams(suitesToTestsMap).
            map(SettingQueryHandler.getProjectIdPrefix(SbtUtil.getSbtProjectIdSeparated(getModule)) + "testOnly" + _)
          val comm = SbtShellCommunication.forProject(project)
          val handler = new SbtTestEventHandler(processHandler)


          val sbtRun = {
            lazy val oldSettings = if (testConfigurationData.useUiWithSbt)
              for {
                _ <- initialize(comm)
                mod <- modifySbtSettingsForUi(comm)
              } yield mod
            else Future.successful(SettingMap())

            lazy val cmdF = commands.map(
              (cmd: String) => comm.command(cmd, {}, SbtShellCommunication.listenerAggregator(handler))
            )
            for {
              old <- oldSettings
              _ <- Future.sequence(cmdF)
              reset <- resetSbtSettingsForUi(comm, old)
            } yield reset
          }
          sbtRun.onComplete(_ => handler.closeRoot())
        }
        res
      }
    }
    state
  }

  protected def getSbtProjectUriAndId: (Option[String], Option[String]) = SbtUtil.getSbtProjectIdSeparated(getModule)

  protected def getClassFileNames(classes: mutable.HashSet[PsiClass]): Seq[String] = classes.map(_.qualifiedName).toSeq

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.writeExternal(this, element)
    XmlSerializer.serializeInto(this, element)
    XmlSerializer.serializeInto(testConfigurationData, element)
    PathMacroManager.getInstance(getProject).collapsePathsRecursively(element)
  }

  override def readExternal(element: Element): Unit = {
    PathMacroManager.getInstance(getProject).expandPaths(element)
    super.readExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.readExternal(this, element)
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

// TODO: simplify: now we have duplicates of method `isInvalidSuite` in companion object, subclasses, subclasses companion objects...
trait SuiteValidityChecker {

  protected[test] def isInvalidSuite(clazz: PsiClass, suiteClass: PsiClass): Boolean = {
    isInvalidClass(clazz) ||
      hasAbstractModifier(clazz) ||
      lackSuitableConstructor(clazz) ||
      !ScalaPsiUtil.isInheritorDeep(clazz, suiteClass)
  }

  private def hasAbstractModifier(clazz: PsiClass) = {
    val list: PsiModifierList = clazz.getModifierList
    list != null && list.hasModifierProperty(PsiModifier.ABSTRACT)
  }

  protected[test] def lackSuitableConstructor(clazz: PsiClass): Boolean

  protected[test] def isInvalidClass(clazz: PsiClass): Boolean = !clazz.isInstanceOf[ScClass]
}

object AbstractTestRunConfiguration extends SuiteValidityChecker {

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

  private[test] trait PropertiesExtension extends SMTRunnerConsoleProperties {
    def getRunConfigurationBase: RunConfigurationBase[_]
  }

  override protected[test] def lackSuitableConstructor(clazz: PsiClass): Boolean = lackSuitableConstructorWithParams(clazz)

  protected[test] def lackSuitableConstructorWithParams(clazz: PsiClass, maxParamsCount: Int = 0): Boolean = {
    val constructors = clazz match {
      case c: ScClass => c.secondaryConstructors.toList ::: c.constructor.toList
      case _ => clazz.getConstructors.toList
    }
    for (con <- constructors) {
      if (con.isConstructor && con.getParameterList.getParametersCount <= maxParamsCount) {
        con match {
          case owner: ScModifierListOwner =>
            if (owner.hasModifierProperty(PsiModifier.PUBLIC)) return false
          case _ =>
        }
      }
    }
    true
  }
}
