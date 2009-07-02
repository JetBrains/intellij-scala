package org.jetbrains.plugins.scala.testingSupport.specs

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
import com.intellij.psi.{PsiPackage, JavaPsiFacade, PsiManager, PsiClass}
import com.intellij.util.PathUtil
import compiler.rt.ScalacRunner
import config.{ScalaCompilerUtil, ScalaConfigUtils}
import jdom.Element
import _root_.scala.collection.mutable.HashSet
import com.intellij.openapi.module.{ModuleUtil, ModuleManager, Module}
import com.intellij.openapi.options.SettingsEditor
import com.intellij.execution.configurations._
import _root_.java.util.Arrays
import com.intellij.facet.FacetManager
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}

import com.intellij.openapi.projectRoots.JavaSdkType
import lang.psi.api.ScalaFile
import com.intellij.execution.filters.TextConsoleBuilderFactory


import com.intellij.vcsUtil.VcsUtil
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import scalaTest.ScalaTestRunConfigurationForm
import script.ScalaScriptRunConfiguration

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */


class SpecsRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS = "org.jetbrains.plugins.scala.testingSupport.specs.SpecsRunner"
  val SUITE_PATH = "org.specs.Specification"
  private var testClassPath = ""
  private var testPackagePath = ""
  private var testArgs = ""
  private var javaOptions = ""

  def getTestClassPath = testClassPath
  def getTestArgs = testArgs
  def getJavaOptions = javaOptions
  def getTestPackagePath: String = testPackagePath
  def setTestClassPath(s: String): Unit = testClassPath = s
  def setTestPackagePath(s: String): Unit = testPackagePath = s
  def setTestArgs(s: String): Unit = testArgs = s
  def setJavaOptions(s: String): Unit = javaOptions = s

  def apply(configuration: SpecsRunConfigurationForm) {
    if (configuration.isClassSelected) {
      setTestClassPath(configuration.getTestClassPath)
      setTestPackagePath("")
    }
    else {
      setTestClassPath("")
      setTestPackagePath(configuration.getTestPackagePath)
    }
    setJavaOptions(configuration.getJavaOptions)
    setTestArgs(configuration.getTestArgs)
    setModule(configuration.getModule)
  }

  def getClazz(path: String): PsiClass = {
    val facade = JavaPsiFacade.getInstance(project)
    facade.findClass(path, GlobalSearchScope.allScope(project))
  }

  def getPackage(path: String): PsiPackage = {
    val facade = JavaPsiFacade.getInstance(project)
    facade.findPackage(path)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    def classNotFoundError {
      throw new ExecutionException("Test class not found.")
    }
    var clazz: PsiClass = null
    var suiteClass: PsiClass = null
    var pack: PsiPackage = null
    try {
      if (testClassPath != "")
        clazz = getClazz(testClassPath)
      else
        pack = getPackage(testPackagePath)
      suiteClass = getClazz(SUITE_PATH)
    }
    catch {
      case e => classNotFoundError
    }
    if (clazz == null && pack == null) classNotFoundError
    if (suiteClass == null)
      throw new ExecutionException("Specs not specified.")
    val classes = new ArrayBuffer[PsiClass]
    if (clazz != null) {
      if (clazz.isInheritor(suiteClass, true)) classes += clazz
    } else {
      for (cl <- pack.getClasses) {
        if (cl.isInheritor(suiteClass, true)) classes += cl
      }
    }

    if (classes.isEmpty) throw new ExecutionException("Not found suite class.")

    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val jarPath = ScalaConfigUtils.getScalaSdkJarPath(getModule)
    val compilerJarPath = ScalaCompilerUtil.getScalaCompilerJarPath(getModule)
    if (jarPath == "" || compilerJarPath == "") throw new ExecutionException("Scala SDK is not specified")

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

        val jarPathForClass = PathUtil.getJarPathForClass(classOf[SpecsRunConfiguration])
        val rtJarPath = PathUtil.getJarPathForClass(classOf[ScalacRunner])
        params.getClassPath.add(rtJarPath)

        val sdkJar = VcsUtil.getVirtualFile(jarPath)
        if (sdkJar != null) {
          params.getClassPath.add(sdkJar)
        }

        val compilerJar = VcsUtil.getVirtualFile(compilerJarPath)
        if (sdkJar != null) {
          params.getClassPath.add(compilerJar)
        }

        params.getClassPath.add(getClassPath(module))


        params.setMainClass(MAIN_CLASS)

        params.getProgramParametersList.add("-s")
        for (cl <- classes) params.getProgramParametersList.add(cl.getQualifiedName)

        return params
      }


      override def execute(executor: Executor, runner: ProgramRunner[_ <: JDOMExternalizable]): ExecutionResult = {
        val processHandler = startProcess
        val config = SpecsRunConfiguration.this
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
    getConfigurationModule.getModule
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new SpecsRunConfiguration(getProject, getFactory, getName)

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

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new SpecsRunConfigurationEditor(project, this)

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