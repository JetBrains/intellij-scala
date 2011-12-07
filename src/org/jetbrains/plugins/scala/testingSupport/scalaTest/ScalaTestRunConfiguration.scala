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
import com.intellij.openapi.util.{JDOMExternalizer, JDOMExternalizable}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiPackage, JavaPsiFacade, PsiClass}
import com.intellij.util.PathUtil
import org.jdom.Element
import com.intellij.execution.configurations._
import java.lang.String
import lang.psi.impl.ScPackageImpl
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
  def getWorkingDirectory: String = workingDirectory
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
    workingDirectory = s
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

  def getClazz(path: String): PsiClass = {
    val facade = JavaPsiFacade.getInstance(project)
    facade.findClass(path, GlobalSearchScope.allScope(project))
  }

  def getPackage(path: String): PsiPackage = {
    ScPackageImpl.findPackage(project, path)
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
          clazz = getClazz(testClassPath)
        case TestKind.ALL_IN_PACKAGE =>
          pack = getPackage(testPackagePath)
      }
      suiteClass = getClazz(SUITE_PATH)
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
      val scope = searchTest match {
        case SearchForTest.IN_WHOLE_PROJECT => GlobalSearchScope.allScope(getProject)
        case SearchForTest.IN_SINGLE_MODULE => GlobalSearchScope.moduleScope(getModule)
        case SearchForTest.ACCROSS_MODULE_DEPENDENCIES => 
          var scope: GlobalSearchScope = GlobalSearchScope.moduleScope(getModule)
          for (module <- ModuleManager.getInstance(getProject).getModules) {
            if (ModuleManager.getInstance(getProject).isModuleDependent(module, getModule)) {
              scope = scope.union(GlobalSearchScope.moduleScope(module))
            }
          }
          scope
      }
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

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters();

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        params.setWorkingDirectory(getWorkingDirectory)
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

        params.getProgramParametersList.add("-s")
        for (cl <- classes) params.getProgramParametersList.add(cl.getQualifiedName)
        
        if (testKind == TestKind.TEST_NAME && testName != "") {
          params.getProgramParametersList.add("-testName")
          params.getProgramParametersList.add(testName)
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
        val config = ScalaTestRunConfiguration.this
        JavaRunConfigurationExtensionManager.getInstance.attachExtensionsToProcess(config, processHandler, runnerSettings)
        val consoleProperties = new SMTRunnerConsoleProperties(config, "Scala", executor);

        // console view
        val testRunnerConsole: BaseTestsOutputConsoleView = SMTestRunnerConnectionUtil.createAndAttachConsole("Scala",
            processHandler, consoleProperties, runnerSettings.asInstanceOf[RunnerSettings[_ <: JDOMExternalizable]],
            getConfigurationSettings)


        new DefaultExecutionResult(testRunnerConsole, processHandler, createActions(testRunnerConsole, processHandler): _*)
      }
    }
    state;
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
  }

  override def readExternal(element: Element) {
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