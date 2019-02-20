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
import com.intellij.openapi.util.{Computable, Getter, JDOMExternalizer}
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jdom.Element
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.testingSupport.locationProvider.ScalaTestLocationProvider
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.{PropertiesExtension, SettingEntry, SettingMap}
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.SearchForTest
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{SbtProcessHandlerWrapper, SbtTestEventHandler}
import org.jetbrains.plugins.scala.testingSupport.{ScalaTestingConfiguration, TestWorkingDirectoryProvider}
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SettingQueryHandler}

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Ksenia.Sautina
  * @since 5/15/12
  */

abstract class AbstractTestRunConfiguration(val project: Project,
                                            val configurationFactory: ConfigurationFactory,
                                            val name: String,
                                            val configurationProducer: TestConfigurationProducer,
                                            private var envs: java.util.Map[String, String] =
                                            new java.util.HashMap[String, String](),
                                            private var addIntegrationTestsClasspath: Boolean = false)
  extends ModuleBasedConfiguration[RunConfigurationModule,Element](name,
    new RunConfigurationModule(project),
    configurationFactory) with ScalaTestingConfiguration {

  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""

  def setupIntegrationTestClassPath(): Unit = addIntegrationTestsClasspath = true

  def currentConfiguration: AbstractTestRunConfiguration = AbstractTestRunConfiguration.this

  def suitePaths: List[String]

  final def javaSuitePaths: java.util.List[String] = {
    import scala.collection.JavaConverters._
    suitePaths.asJava
  }

  def mainClass: String

  def reporterClass: String

  def errorMessage: String

  private var testArgs = ""
  private var javaOptions = ""
  private var workingDirectory = ""
  @BeanProperty
  var testConfigurationData: TestConfigurationData = new ClassTestData(this)

  def getTestClassPath: String = testConfigurationData.testClassPath//testClassPath

  def getTestPackagePath: String = testConfigurationData.testPackagePath//testPackagePath

  def getTestArgs: String = testArgs

  def getJavaOptions: String = javaOptions

  def getWorkingDirectory: String = ExternalizablePath.localPathValue(workingDirectory)

  def allowsSbtUiRun: Boolean = false

  def setTestArgs(s: String) {
    testArgs = s
  }

  def setJavaOptions(s: String) {
    javaOptions = s
  }

  def setWorkingDirectory(s: String) {
    workingDirectory = ExternalizablePath.urlValue(s)
  }

  def initWorkingDir(): Unit = if (workingDirectory == null || workingDirectory.trim.isEmpty) setWorkingDirectory(provideDefaultWorkingDir)

  private def provideDefaultWorkingDir = {
    val module = getModule
    TestWorkingDirectoryProvider.EP_NAME.getExtensions.find(_.getWorkingDirectory(module) != null) match {
      case Some(provider) => provider.getWorkingDirectory(module)
      case _ => Option(getProject.getBaseDir).map(_.getPath).getOrElse("")
    }
  }

  @BeanProperty
  var searchTest: SearchForTest = SearchForTest.ACCROSS_MODULE_DEPENDENCIES
  @BeanProperty
  var showProgressMessages = true
  @BeanProperty
  var useSbt: Boolean = false
  @BeanProperty
  var useUiWithSbt = false
  @BeanProperty
  var jrePath: String = _

  private var generatedName: String = ""

  def setGeneratedName(name: String) {
    generatedName = name
  }

  override def isGeneratedName: Boolean = getName == null || getName.equals(suggestedName)

  override def suggestedName: String = generatedName

  def apply(configuration: TestRunConfigurationForm) {
    setSearchTest(configuration.getSearchForTest)
    setJavaOptions(configuration.getJavaOptions)
    setTestArgs(configuration.getTestArgs)
    setModule(configuration.getModule)
    setJrePath(configuration.getJrePath)
    val workDir = configuration.getWorkingDirectory
    setWorkingDirectory(
      if (workDir != null && !workDir.trim.isEmpty) {
        workDir
      } else {
        provideDefaultWorkingDir
      }
    )
    setEnvVariables(configuration.getEnvironmentVariables)
    setShowProgressMessages(configuration.getShowProgressMessages)
    setUseSbt(configuration.getUseSbt)
    setUseUiWithSbt(configuration.getUseUiWithSbt)
    testConfigurationData = TestConfigurationData.fromForm(configuration, this)
  }

  protected[test] def getClazz(path: String, withDependencies: Boolean): PsiClass = {
    val classes = ScalaPsiManager.instance(project).getCachedClasses(getScope(withDependencies), path)
    val objectClasses = classes.filter(_.isInstanceOf[ScObject])
    val nonObjectClasses = classes.filter(!_.isInstanceOf[ScObject])
    if (nonObjectClasses.nonEmpty) nonObjectClasses(0) else if (objectClasses.nonEmpty) objectClasses(0) else null
  }

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

  def expandPath(_path: String): String = {
    var path = _path
    path = PathMacroManager.getInstance(project).expandPath(path)
    if (getModule != null) {
      path = PathMacroManager.getInstance(getModule).expandPath(path)
    }
    path
  }

  def getEnvVariables: java.util.Map[String, String] = envs

  def setEnvVariables(variables: java.util.Map[String, String]): Unit = {
    envs = variables
  }

  def getModule: Module = {
    getConfigurationModule.getModule
  }

  def getValidModules: java.util.List[Module] =
    getProject.modulesWithScala.asJava

  def classKey: String = "-s"

  def testNameKey: String = "-testName"

  protected def sbtClassKey = " "

  protected def sbtTestNameKey = " "

  protected def modifySbtSettingsForUi(comm: SbtShellCommunication): Future[SettingMap] =
    Future(Map.empty)

  protected def initialize(comm: SbtShellCommunication): Future[String] =
    comm.command("initialize", showShell = false)

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
    showHandler.getSettingValue().flatMap {
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
      def compute: Array[Module] = {
        searchTest match {
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

  protected[test] def getSuiteClass: PsiClass = {
    val suiteClasses = suitePaths.map(suitePath => getClazz(suitePath, withDependencies = true)).filter(_ != null)

    if (suiteClasses.isEmpty) {
      throw new RuntimeConfigurationException(errorMessage)
    }

    if (suiteClasses.size > 1) {
      throw new RuntimeConfigurationException("Multiple suite traits detected: " + suiteClasses)
    }

    suiteClasses.head
  }

  override def checkConfiguration() {
    testConfigurationData.checkSuiteAndTestName()
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this)
  }

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = {
    val group: SettingsEditorGroup[AbstractTestRunConfiguration] = new SettingsEditorGroup
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"),
      new AbstractTestRunConfigurationEditor(project, this))
    JavaRunConfigurationExtensionManager.getInstance.appendEditors(this, group)
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel[AbstractTestRunConfiguration])
    group
  }

  protected[test] def isInvalidSuite(clazz: PsiClass): Boolean = AbstractTestRunConfiguration.isInvalidSuite(clazz, getSuiteClass)

  private def addParametersString(pString: String, add: String => Unit): Unit = ParametersList.parse(pString).foreach(add)

  def addClassesAndTests(classToTests: Map[String, Set[String]], add: String => Unit): Unit = {
    for ((className, tests) <- classToTests) {
      add(classKey)
      add(className)
      for (test <- tests if test != "") {
        add(testNameKey)
        add(test)
      }
    }
  }

  protected def escapeTestName(test: String): String = if (test.contains(" ")) "\"" + test + "\"" else test

  protected def escapeClassAndTest(input: String): String = input

  def buildSbtParams(classToTests: Map[String, Set[String]]): Seq[String] = {
    (for ((aClass, tests) <- classToTests) yield {
      if (tests.isEmpty) Seq(s"$sbtClassKey$aClass")
      else for (test <- tests) yield sbtClassKey + escapeClassAndTest(aClass + sbtTestNameKey + escapeTestName(test))
    }).flatten.toSeq
  }

  protected[test] def classNotFoundError = {
    throw new ExecutionException(s"Test class not found: $getTestClassPath")
  }

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val state = new JavaCommandLineState(env) with AbstractTestRunConfiguration.TestCommandLinePatcher {
      private def suitesToTestsMap: Map[String, Set[String]] = {
        val failedTests = getFailedTests.groupBy(_._1).map { case (aClass, tests) => (aClass, tests.map(_._2).toSet) }.filter(_._2.nonEmpty)
        if (failedTests.nonEmpty) failedTests else testConfigurationData.getTestMap()
      }

      override val getClasses: Seq[String] = testConfigurationData.getTestMap().keys.toSeq

      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters()

        params.setCharset(null)
        var vmParams = getJavaOptions

        //expand macros
        vmParams = PathMacroManager.getInstance(project).expandPath(vmParams)

        if (module != null) {
          vmParams = PathMacroManager.getInstance(module).expandPath(vmParams)
        }

        val mutableEnvVariables = getEnvVariables
        params.setEnv(mutableEnvVariables)

        //expand environment variables in vmParams
        params.getEnv.entrySet.forEach { entry =>
          vmParams = StringUtil.replace(vmParams, "$" + entry.getKey + "$", entry.getValue, false)
        }

        params.getVMParametersList.addParametersString(vmParams)
        val wDir = getWorkingDirectory
        params.setWorkingDirectory(expandPath(wDir))

        //                params.getVMParametersList.addParametersString("-agentlib:jdwp=transport=dt_socket,server=n,address=UNIT-549.Labs.IntelliJ.Net:5005,suspend=y")

        val rtJarPath = ScalaUtil.runnersPath()
        params.getClassPath.add(rtJarPath)
        //a workaround to add jars for integration tests
        if (addIntegrationTestsClasspath) params.getClassPath.add(ScalaUtil.runnersPath())

        ManagingFS.getInstance match {
          case fs: PersistentFSImpl => fs.incStructuralModificationCount()
          case _                    =>
        }

        val maybeCustomSdk = ProjectJdkTable.getInstance().findJdk(jrePath).toOption
        searchTest match {
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

        params.setMainClass(mainClass)

        def addParameters(add: String => Unit, after: () => Unit): Unit = {
          addClassesAndTests(suitesToTestsMap, add)
          if (reporterClass != null) {
            add("-C")
            add(reporterClass)
          }

          add("-showProgressMessages")
          add(showProgressMessages.toString)
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

        addParametersString(getTestArgs, myAdd)
        addParameters(myAdd, myAfter)

        for (ext <- RunConfigurationExtension.EP_NAME.getExtensionList.asScala) {
          ext.updateJavaParameters(currentConfiguration, params, getRunnerSettings)
        }

        params
      }

      override def execute(executor: Executor, runner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
        val processHandler = if (useSbt) {
          //use a process running sbt
          val sbtProcessManager = SbtProcessManager.forProject(project)
          //make sure the process is initialized
          val shellRunner = sbtProcessManager.acquireShellRunner
          //TODO: this null is really weird
          SbtProcessHandlerWrapper(shellRunner createProcessHandler null)
        } else startProcess()
        val runnerSettings = getRunnerSettings
        if (getConfiguration == null) setConfiguration(currentConfiguration)
        val config = getConfiguration
        JavaRunConfigurationExtensionManager.getInstance.
          attachExtensionsToProcess(currentConfiguration, processHandler, runnerSettings)
        val consoleProperties = new SMTRunnerConsoleProperties(currentConfiguration, "Scala", executor)
          with PropertiesExtension {
          override def getTestLocator = new ScalaTestLocationProvider

          def getRunConfigurationBase: RunConfigurationBase[_] = config
        }

        consoleProperties.setIdBasedTestTree(true)

        // console view
        val consoleView = if (useSbt && !useUiWithSbt) {
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
              def get: TestFrameworkRunningModel = {
                testConsole.asInstanceOf[SMTRunnerConsoleView].getResultsViewer
              }
            })
            res.setRestartActions(rerunFailedTestsAction, new ToggleAutoTestAction() {
              override def isDelayApplicable: Boolean = false

              override def getAutoTestManager(project: Project): AbstractAutoTestManager = JavaAutoRunManager.getInstance(project)
            })
          case _ =>
        }
        if (useSbt) {
          Stats.trigger(FeatureKey.sbtShellTestRunConfig)

          val commands = buildSbtParams(suitesToTestsMap).
            map(SettingQueryHandler.getProjectIdPrefix(SbtUtil.getSbtProjectIdSeparated(getModule)) + "testOnly" + _)
          val comm = SbtShellCommunication.forProject(project)
          val handler = new SbtTestEventHandler(processHandler)


          val sbtRun = {
            lazy val oldSettings = if (useUiWithSbt)
              for {
                _ <- initialize(comm)
                mod <- modifySbtSettingsForUi(comm)
              } yield mod
            else Future.successful(SettingMap())

            lazy val cmdF = commands.map(
              comm.command(_, {}, SbtShellCommunication.listenerAggregator(handler), showShell = false)
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

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.writeExternal(this, element)
    JDOMExternalizer.write(element, "path", getTestClassPath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "params", getTestArgs)
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
    JDOMExternalizer.write(element, "searchForTest", searchTest.toString)
    JDOMExternalizer.write(element, "showProgressMessages", showProgressMessages.toString)
    JDOMExternalizer.write(element, "useSbt", useSbt)
    JDOMExternalizer.write(element, "useUiWithSbt", useUiWithSbt)
    JDOMExternalizer.write(element, "jrePath", jrePath)
    JDOMExternalizer.writeMap(element, envs, "envs", "envVar")
    TestConfigurationData.writeExternal(element, testConfigurationData)
    PathMacroManager.getInstance(getProject).collapsePathsRecursively(element)
  }

  override def readExternal(element: Element) {

    PathMacroManager.getInstance(getProject).expandPaths(element)
    super.readExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.readExternal(this, element)
    readModule(element)
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    testArgs = JDOMExternalizer.readString(element, "params")
    workingDirectory = JDOMExternalizer.readString(element, "workingDirectory")
    JDOMExternalizer.readMap(element, envs, "envs", "envVar")
    val s = JDOMExternalizer.readString(element, "searchForTest")
    for (search <- SearchForTest.values()) {
      if (search.toString == s) searchTest = search
    }
    testConfigurationData = TestConfigurationData.readExternal(element, this)//TestKind.fromString(Option(JDOMExternalizer.readString(element, "testKind")).getOrElse("Class"))
    showProgressMessages = JDOMExternalizer.readBoolean(element, "showProgressMessages")
    useSbt = JDOMExternalizer.readBoolean(element, "useSbt")
    useUiWithSbt = JDOMExternalizer.readBoolean(element, "useUiWithSbt")
    jrePath = JDOMExternalizer.readString(element, "jrePath")
  }
}

trait SuiteValidityChecker {
  protected[test] def isInvalidSuite(clazz: PsiClass, suiteClass: PsiClass): Boolean = {
    isInvalidClass(clazz) || {
      val list: PsiModifierList = clazz.getModifierList
      list != null && list.hasModifierProperty(PsiModifier.ABSTRACT) || lackSuitableConstructor(clazz)
    } || !ScalaPsiUtil.isInheritorDeep(clazz, suiteClass)
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

    def setFailedTests(failedTests: Seq[(String, String)]) {
      this.failedTests = Option(failedTests).map(_.distinct).orNull
    }

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
