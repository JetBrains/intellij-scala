package org.jetbrains.plugins.scala.testingSupport.scalaTest


import _root_.java.io.File
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.execution._
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.{ProgramRunner, ExecutionEnvironment}
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{JDOMExternalizer, JDOMExternalizable}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiManager, PsiClass}
import com.intellij.util.PathUtil
import jdom.Element
import _root_.scala.collection.mutable.HashSet
import com.intellij.openapi.module.{ModuleUtil, ModuleManager, Module}
import com.intellij.openapi.options.SettingsEditor
import config.{ScalaConfigUtils, ScalaSDK}

import com.intellij.execution.configurations._
import _root_.java.util.Arrays
import com.intellij.facet.FacetManager
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}

import com.intellij.openapi.projectRoots.JavaSdkType
import lang.psi.api.ScalaFile
import com.intellij.execution.filters.TextConsoleBuilderFactory


import com.intellij.vcsUtil.VcsUtil
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import script.ScalaScriptRunConfiguration

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS = "org.scalatest.tools.Runner"
  val SUITE_PATH = "org.scalatest.Suite"
  val REPORTER = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter"
  private var testClassPath = ""
  private var testArgs = ""
  private var javaOptions = ""

  def getTestClassPath = testClassPath
  def getTestArgs = testArgs
  def getJavaOptions = javaOptions
  def setTestClassPath(s: String): Unit = testClassPath = s
  def setTestArgs(s: String): Unit = testArgs = s
  def setJavaOptions(s: String): Unit = javaOptions = s

  def apply(configuration: ScalaTestRunConfigurationForm) {
    setTestClassPath(configuration.getTestClassPath)
    setJavaOptions(configuration.getJavaOptions)
    setTestArgs(configuration.getTestArgs)
  }

  def getClazz(path: String): PsiClass = {
    val facade = JavaPsiFacade.getInstance(project)
    facade.findClass(path, GlobalSearchScope.allScope(project))
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    def classNotFoundError {
      throw new ExecutionException("Test class not found.")
    }
    var clazz: PsiClass = null
    var suiteClass: PsiClass = null
    try {
      clazz = getClazz(testClassPath)
      suiteClass = getClazz(SUITE_PATH)
    }
    catch {
      case e => classNotFoundError
    }
    if (clazz == null) classNotFoundError
    if (suiteClass == null)
      throw new ExecutionException("ScalaTest not specified.")
    if (!clazz.isInheritor(suiteClass, true)) throw new ExecutionException("Not found suite class.")

    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")
    val scalaSdkPath = getScalaInstallPath
    if (scalaSdkPath == "") throw new ExecutionException("Scala SDK is not specified")
    val rootManager = ModuleRootManager.getInstance(module);
    val sdk = rootManager.getSdk();
    if (sdk == null || !(sdk.getSdkType.isInstanceOf[JavaSdkType])) {
      throw CantRunException.noJdkForModule(module);
    }
    val sdkType = sdk.getSdkType

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters();

        params.setJdk(sdk)

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        //params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
        val list = new java.util.ArrayList[String]
        val dir: VirtualFile = VcsUtil.getVirtualFile(scalaSdkPath + File.separator + "lib")
        val jarPathForClass = PathUtil.getJarPathForClass(classOf[ScalaTestRunConfiguration])
        val virtFile = VcsUtil.getVirtualFile(jarPathForClass)
        if (virtFile.getExtension != "jar") { //so it's debug mode, we can free use ScalaTestReporter class
          val rtJarPath = PathUtil.getJarPathForClass(classOf[ScalaTestReporter])
          params.getClassPath.add(rtJarPath)
        } else { //so we must to find jar
          val rtJarPath = jarPathForClass.substring(0, jarPathForClass.lastIndexOf(File.separator)) + File.separator + "runners.jar"
          params.getClassPath.add(rtJarPath)
        }
        for (child <- dir.getChildren) {
          params.getClassPath.add(child)
        }
        params.getClassPath.add(getClassPath(module))


        params.setMainClass(MAIN_CLASS)

        params.getProgramParametersList.add("-s")
        params.getProgramParametersList.add(testClassPath)
        params.getProgramParametersList.add("-rYZTFGUPBISAR")
        params.getProgramParametersList.add(REPORTER)
        params.getProgramParametersList.addParametersString(testArgs)
        return params
      }


      override def execute(executor: Executor, runner: ProgramRunner[_ <: JDOMExternalizable]): ExecutionResult = {
        val processHandler = startProcess
        val config = ScalaTestRunConfiguration.this
        val consoleProperties = new SMTRunnerConsoleProperties(config);

        // Results viewer component
        val resultsViewer = new SMTestRunnerResultsForm(config, consoleProperties,
                                                        getRunnerSettings, getConfigurationSettings)

        // console view
        val testRunnerConsole = SMTestRunnerConnectionUtil.attachRunner(project, processHandler, consoleProperties, resultsViewer)
        new DefaultExecutionResult(testRunnerConsole, processHandler, createActions(testRunnerConsole, processHandler))
      }
    }
    return state;
  }

  def getModule: Module = {
    var module: Module = null
    try {
      module = JavaRunConfigurationModule.getModulesForClass(project, testClassPath).toArray()(0).asInstanceOf[Module]
    }
    catch {
      case e =>
    }
    if (module == null) module = getConfigurationModule.getModule
    return module
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new ScalaTestRunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = {
    val result = new ArrayBuffer[Module]

    val modules = ModuleManager.getInstance(getProject).getModules
    for (module <- modules) {
      val facetManager = FacetManager.getInstance(module)
      if (facetManager.getFacetByType(org.jetbrains.plugins.scala.config.ScalaFacet.ID) != null) {
        result += module
      }
    }
    return Arrays.asList(result.toArray: _*)
  }

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaTestRunConfigurationEditor(project, this)

  def getSdk(): ScalaSDK = {
    if (getModule != null) ScalaConfigUtils.getScalaSDKs(getModule).apply(0)
    else null
  }

  def getScalaInstallPath(): String = {
    if (getModule != null) {
      val sdk = ScalaConfigUtils.getScalaInstallPath(getModule)
      var exePath = sdk.replace('\\', File.separatorChar)
      return exePath
    }
    else ""
  }


  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "path", getTestClassPath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "params", getTestArgs)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    readModule(element)
    testClassPath = JDOMExternalizer.readString(element, "path")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    testArgs = JDOMExternalizer.readString(element, "params")
  }

  private def getClassPath(module: Module): String = {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val entries = moduleRootManager.getOrderEntries
    val cpVFiles = new HashSet[VirtualFile];
    for (orderEntry <- entries) {
      cpVFiles ++= orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)
    }
    val res = new StringBuilder("")
    for (file <- cpVFiles) {
      var path = file.getPath
      val jarSeparatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR)
      if (jarSeparatorIndex > 0) {
        path = path.substring(0, jarSeparatorIndex)
      }
      res.append(path).append(File.pathSeparator)
    }
    return res.toString
  }
}