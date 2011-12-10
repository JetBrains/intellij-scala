package org.jetbrains.plugins.scala
package testingSupport
package scalaTest


import collection.mutable.ArrayBuffer
import com.intellij.execution._
import com.intellij.execution.runners.{ProgramRunner, ExecutionEnvironment}
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiPackage, JavaPsiFacade, PsiClass}
import com.intellij.util.PathUtil
import org.jdom.Element
import com.intellij.execution.configurations._
import java.lang.String
import specs.JavaSpecsRunner
import config.ScalaFacet
import collection.JavaConversions._
import lang.psi.ScalaPsiUtil
import com.intellij.openapi.options.{SettingsEditorGroup, SettingsEditor}
import com.intellij.diagnostic.logging.LogConfigurationPanel
import com.intellij.openapi.extensions.Extensions
import reflect.BeanProperty
import com.intellij.openapi.module.{ModuleManager, Module}
import com.intellij.openapi.projectRoots.Sdk
import scalaTest.ScalaTestRunConfigurationForm.{TestKind, SearchForTest}
import testframework.sm.runner.ui.SMTRunnerConsoleView
import testframework.TestFrameworkRunningModel
import com.intellij.openapi.util.{Getter, JDOMExternalizer, JDOMExternalizable}
import scalaTest.ScalaTestRunConfiguration.ScalaPropertiesExtension
import com.intellij.openapi.components.PathMacroManager
import lang.psi.impl.{ScalaPsiManager, ScPackageImpl}
import lang.psi.api.toplevel.typedef.ScObject

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project),
          configurationFactory) with ScalaTestingConfiguration {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val mainClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner"
  val reporterClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter"
  val SUITE_PATH = "org.scalatest.Suite"

  private var testClassPath = ""
  private var testPackagePath = ""
  private var testArgs = ""
  private var javaOptions = ""
  private var workingDirectory = {
    val base = getProject.getBaseDir
    if (base != null) base.getPath
    else ""
  }

  @BeanProperty
  var searchTest: SearchForTest = SearchForTest.ACCROSS_MODULE_DEPENDENCIES

  @BeanProperty
  var testName = ""

  @BeanProperty
  var testKind = TestKind.CLASS

  def getTestClassPath = testClassPath
  def getTestPackagePath = testPackagePath
  def getTestArgs = testArgs
  def getJavaOptions = javaOptions
  def getWorkingDirectory: String = {
    ExternalizablePath.localPathValue(workingDirectory)
  }
  def setTestClassPath(s: String) {testClassPath = s}
  def setTestPackagePath(s: String) {
    testPackagePath = s
  }
  def setTestArgs(s: String) {
    testArgs = s
  }
  def setJavaOptions(s: String) {
    javaOptions = s
  }
  def setWorkingDirectory(s: String) {
    workingDirectory = ExternalizablePath.urlValue(s)
  }

  private var generatedName: String = ""
  override def getGeneratedName = generatedName
  def setGeneratedName(name: String) {generatedName = name}
  override def isGeneratedName = getName == null || getName.equals(suggestedName)
  override def suggestedName = getGeneratedName

  def apply(configuration: ScalaTestRunConfigurationForm) {
    testKind = configuration.getSelectedKind
    setTestClassPath(configuration.getTestClassPath)
    setTestPackagePath(configuration.getTestPackagePath)
    setSearchTest(configuration.getSearchForTest)
    setJavaOptions(configuration.getJavaOptions)
    setTestArgs(configuration.getTestArgs)
    setModule(configuration.getModule)
    setWorkingDirectory(configuration.getWorkingDirectory)
    setTestName(configuration.getTestName)
  }

  def getClazz(path: String, withDependencies: Boolean): PsiClass = {
    val classes = ScalaPsiManager.instance(project).getCachedClasses(getScope(withDependencies), path).
      filter(!_.isInstanceOf[ScObject])
    if (classes.isEmpty) null
    else classes(0)
  }

  def getPackage(path: String): PsiPackage = {
    ScPackageImpl.findPackage(project, path)
  }
  
  private def getScope(withDependencies: Boolean): GlobalSearchScope = {
    def mScope(module: Module) = {
      if (withDependencies) GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
      else GlobalSearchScope.moduleScope(module)
    }
    testKind match {
      case TestKind.ALL_IN_PACKAGE =>
        searchTest match {
          case SearchForTest.IN_WHOLE_PROJECT => GlobalSearchScope.allScope(project)
          case SearchForTest.IN_SINGLE_MODULE if getModule != null => mScope(getModule)
          case SearchForTest.ACCROSS_MODULE_DEPENDENCIES if getModule != null =>
            var scope: GlobalSearchScope = mScope(getModule)
            for (module <- ModuleManager.getInstance(getProject).getModules) {
              if (ModuleManager.getInstance(getProject).isModuleDependent(module, getModule)) {
                scope = scope.union(mScope(module))
              }
            }
            scope
          case _ => GlobalSearchScope.allScope(project)
        }
      case _ =>
        if (getModule != null) mScope(getModule)
        else GlobalSearchScope.allScope(project)
    }
  }

  private def expandPath(_path: String): String = {
    var path = _path
    path = PathMacroManager.getInstance(project).expandPath(path)
    if (getModule != null) {
      path = PathMacroManager.getInstance(getModule).expandPath(path)
    }
    path
  }

  override def checkConfiguration() {
    super.checkConfiguration()

     val suiteClass = getClazz(SUITE_PATH, true)

    if (suiteClass == null) {
      throw new RuntimeConfigurationException("ScalaTest is not specified")
    }

    testKind match {
      case TestKind.ALL_IN_PACKAGE =>
        searchTest match {
          case SearchForTest.IN_WHOLE_PROJECT =>
          case SearchForTest.IN_SINGLE_MODULE | SearchForTest.ACCROSS_MODULE_DEPENDENCIES =>
            if (getModule == null) {
              throw new RuntimeConfigurationException("Module is not specified")
            }
        }
        val pack = JavaPsiFacade.getInstance(project).findPackage(getTestPackagePath)
        if (pack == null) {
          throw new RuntimeConfigurationException("Package doesn't exist")
        }
      case TestKind.CLASS | TestKind.TEST_NAME =>
        if (getModule == null) {
          throw new RuntimeConfigurationException("Module is not specified")
        }
        if (getTestClassPath == "") {
          throw new RuntimeConfigurationException("Test Class is not specified")
        }
        val clazz = getClazz(getTestClassPath, false)
        if (clazz == null) {
          throw new RuntimeConfigurationException("Class %s is not found in module %s".format(getTestClassPath, 
            getModule.getName))
        }

        if (!ScalaPsiUtil.cachedDeepIsInheritor(clazz, suiteClass)) {
          throw new RuntimeConfigurationException("Class %s is not inheritor of Suite trait".format(getTestClassPath))
        }
        //todo: check test name
    }
    
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    def classNotFoundError() {
      throw new ExecutionException("Test class not found.")
    }
    var clazz: PsiClass = null
    var suiteClass: PsiClass = null
    var pack: PsiPackage = null
    try {
      testKind match {
        case TestKind.CLASS | TestKind.TEST_NAME =>
          clazz = getClazz(testClassPath, false)
        case TestKind.ALL_IN_PACKAGE =>
          pack = getPackage(testPackagePath)
      }
      suiteClass = getClazz(SUITE_PATH, true)
    }
    catch {
      case e => classNotFoundError()
    }
    if (clazz == null && pack == null) classNotFoundError()
    if (suiteClass == null)
      throw new ExecutionException("ScalaTest not specified.")
    val classes = new ArrayBuffer[PsiClass]
    if (clazz != null) {
      if (ScalaPsiUtil.cachedDeepIsInheritor(clazz, suiteClass)) classes += clazz
    } else {
      val scope = getScope(false)
      def getClasses(pack: PsiPackage): Seq[PsiClass] = {
        val buffer = new ArrayBuffer[PsiClass]
        
        buffer ++= pack.getClasses(scope)
        for (p <- pack.getSubPackages) {
          buffer ++= getClasses(p)
        }
        buffer.toSeq
      }
      for (cl <- getClasses(pack)) {
        if (ScalaPsiUtil.cachedDeepIsInheritor(cl, suiteClass)) classes += cl
      }
    }

    if (classes.isEmpty) throw new ExecutionException("Not found suite class.")

    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val state = new JavaCommandLineState(env) with ScalaTestRunConfiguration.ScalaTestCommandLinePatcher {
      val getClasses: Seq[String] = classes.map(_.getQualifiedName)

      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters();

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        val wDir = getWorkingDirectory
        params.setWorkingDirectory(expandPath(wDir))
//        params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug " +
//          "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5010")

        val rtJarPath = PathUtil.getJarPathForClass(classOf[JavaSpecsRunner])
        params.getClassPath.add(rtJarPath)
        searchTest match {
          case SearchForTest.IN_WHOLE_PROJECT =>
            var jdk: Sdk = null
            for (module <- ModuleManager.getInstance(project).getModules if jdk == null) {
              jdk = JavaParameters.getModuleJdk(module)
            }
            params.configureByProject(project, JavaParameters.JDK_AND_CLASSES_AND_TESTS, jdk)
          case _ =>
            params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
            for (module <- ModuleManager.getInstance(getProject).getModules) {
              if (ModuleManager.getInstance(getProject).isModuleDependent(module, getModule)) {
                params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                  JavaParameters.getModuleJdk(getModule))
              }
            }
        }

        params.setMainClass(mainClass)

        if (getFailedTests == null) {
          params.getProgramParametersList.add("-s")
          for (cl <- getClasses) params.getProgramParametersList.add(cl)
        
          if (testKind == TestKind.TEST_NAME && testName != "") {
            params.getProgramParametersList.add("-testName")
            params.getProgramParametersList.add(testName)
          }
        } else {
          params.getProgramParametersList.add("-failedTests")
          for (failed <- getFailedTests) {
            params.getProgramParametersList.add(failed._1)
            params.getProgramParametersList.add(failed._2)
          }
        }

        params.getProgramParametersList.add("-r")
        params.getProgramParametersList.add(reporterClass)
        params.getProgramParametersList.addParametersString(testArgs)
        for (ext <- Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
          ext.updateJavaParameters(ScalaTestRunConfiguration.this, params, getRunnerSettings)
        }
        params
      }


      override def execute(executor: Executor, runner: ProgramRunner[_ <: JDOMExternalizable]): ExecutionResult = {
        val processHandler = startProcess
        val runnerSettings = getRunnerSettings
        if (getConfiguration() == null) setConfiguration(ScalaTestRunConfiguration.this)
        val config = getConfiguration()
        JavaRunConfigurationExtensionManager.getInstance.
          attachExtensionsToProcess(ScalaTestRunConfiguration.this, processHandler, runnerSettings)
        val consoleProperties = new SMTRunnerConsoleProperties(ScalaTestRunConfiguration.this, "Scala", executor)
          with ScalaPropertiesExtension {
          def getRunConfigurationBase: RunConfigurationBase = config
        }

        // console view
        val consoleView: BaseTestsOutputConsoleView = SMTestRunnerConnectionUtil.createAndAttachConsole("Scala",
            processHandler, consoleProperties, runnerSettings.asInstanceOf[RunnerSettings[_ <: JDOMExternalizable]],
            getConfigurationSettings)

        val res = new DefaultExecutionResult(consoleView, processHandler,
          createActions(consoleView, processHandler, executor): _*)

        val rerunFailedTestsAction = new ScalaTestRerunFailedTestsAction(consoleView.getComponent)
        rerunFailedTestsAction.init(consoleView.getProperties, getRunnerSettings, getConfigurationSettings)
        rerunFailedTestsAction.setModelProvider(new Getter[TestFrameworkRunningModel] {
          def get: TestFrameworkRunningModel = {
            consoleView.asInstanceOf[SMTRunnerConsoleView].getResultsViewer
          }
        })
        res.setRestartActions(rerunFailedTestsAction)
        res
      }
    }
    state
  }

  def getModule: Module = {
    getConfigurationModule.getModule
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new ScalaTestRunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList

  override def getModules: Array[Module] = {
    searchTest match {
      case SearchForTest.ACCROSS_MODULE_DEPENDENCIES if getModule != null =>
        val buffer = new ArrayBuffer[Module]()
        buffer += getModule
        for (module <- ModuleManager.getInstance(getProject).getModules) {
          if (ModuleManager.getInstance(getProject).isModuleDependent(module, getModule)) {
            buffer += module
          }
        }
        buffer.toArray
      case SearchForTest.IN_SINGLE_MODULE if getModule != null => Array(getModule)
      case SearchForTest.IN_WHOLE_PROJECT =>  ModuleManager.getInstance(getProject).getModules
      case _ => Array.empty
    }
  }

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = {
    val group: SettingsEditorGroup[ScalaTestRunConfiguration] = new SettingsEditorGroup
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new ScalaTestRunConfigurationEditor(project, this))
    JavaRunConfigurationExtensionManager.getInstance.appendEditors(this, group)
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel)
    group
  }

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.writeExternal(this, element)
    writeModule(element)
    JDOMExternalizer.write(element, "path", getTestClassPath)
    JDOMExternalizer.write(element, "package", getTestPackagePath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "params", getTestArgs)
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
    JDOMExternalizer.write(element, "searchForTest", searchTest.toString)
    JDOMExternalizer.write(element, "testName", testName)
    JDOMExternalizer.write(element, "testKind", testKind.toString)
    PathMacroManager.getInstance(getProject).collapsePathsRecursively(element)
  }

  override def readExternal(element: Element) {
    PathMacroManager.getInstance(getProject).expandPaths(element)
    super.readExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.writeExternal(this, element)
    readModule(element)
    testClassPath = JDOMExternalizer.readString(element, "path")
    testPackagePath = JDOMExternalizer.readString(element, "package")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    testArgs = JDOMExternalizer.readString(element, "params")
    workingDirectory = JDOMExternalizer.readString(element, "workingDirectory")
    val s = JDOMExternalizer.readString(element, "searchForTest")
    for (search <- SearchForTest.values()) {
      if (search.toString == s) searchTest = search
    }
    testName = Option(JDOMExternalizer.readString(element, "testName")).getOrElse("")
    testKind = TestKind.fromString(Option(JDOMExternalizer.readString(element, "testKind")).getOrElse("Class"))
  }
}

object ScalaTestRunConfiguration {
  private[scalaTest] trait ScalaTestCommandLinePatcher {
    private var failedTests: Seq[(String, String)] = null
    def setFailedTests(failedTests: Seq[(String, String)]) {this.failedTests = failedTests}
    def getFailedTests = failedTests
    
    def getClasses: Seq[String]

    @BeanProperty
    var configuration: RunConfigurationBase = null
  }

  private[scalaTest] trait ScalaPropertiesExtension {
    def getRunConfigurationBase: RunConfigurationBase
  }
}