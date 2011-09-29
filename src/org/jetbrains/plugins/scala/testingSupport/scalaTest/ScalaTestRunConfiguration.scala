package org.jetbrains.plugins.scala
package testingSupport
package scalaTest


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
import org.jdom.Element
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
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import script.ScalaScriptRunConfiguration
import com.intellij.openapi.roots.libraries.{LibrariesHelper, Library, LibraryUtil}
import java.lang.String
import lang.psi.impl.ScPackageImpl
import specs.JavaSpecsRunner
import config.ScalaFacet
import collection.JavaConversions._
import lang.psi.ScalaPsiUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  def mainClass(scalaTestVersion: String = "10", scalaVersion: String = "28") = {
    "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTest" + scalaTestVersion + "Scala" + scalaVersion + "Runner"
  }
  def reporterClass(scalaTestVersion: String = "10", scalaVersion: String = "28") = {
    "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTest" + scalaTestVersion + "Scala" + scalaVersion + "Reporter"
  }
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
  private var scalaTestVersion = false

  def getTestClassPath = testClassPath
  def getTestPackagePath = testPackagePath
  def getTestArgs = testArgs
  def getJavaOptions = javaOptions
  def getScalaTestVersion: Boolean = scalaTestVersion
  def getWorkingDirectory: String = workingDirectory
  def setTestClassPath(s: String) {testClassPath = s}
  def setTestPackagePath(s: String): Unit = testPackagePath = s
  def setTestArgs(s: String): Unit = testArgs = s
  def setJavaOptions(s: String): Unit = javaOptions = s
  def setScalaTestVersion(b: Boolean): Unit = scalaTestVersion = b
  def setWorkingDirectory(s: String): Unit = workingDirectory = s

  private var generatedName: String = ""
  override def getGeneratedName = generatedName
  def setGeneratedName(name: String) {generatedName = name}
  override def isGeneratedName = getName == null || getName.equals(suggestedName)
  override def suggestedName = getGeneratedName

  def apply(configuration: ScalaTestRunConfigurationForm) {
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
    setScalaTestVersion(configuration.getScalaTestVersion)
    setModule(configuration.getModule)
    setWorkingDirectory(configuration.getWorkingDirectory)
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
      if (testClassPath != "")
        clazz = getClazz(testClassPath)
      else
        pack = getPackage(testPackagePath)
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
      def getClasses(pack: PsiPackage): Seq[PsiClass] = {
        val buffer = new ArrayBuffer[PsiClass]
        buffer ++= pack.getClasses
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

    val facet = ScalaFacet.findIn(module).getOrElse {
      throw new ExecutionException("No Scala facet configured for module " + module.getName)
    }

    //versions detection for scala compiler and for ScalaTest
    val version: String = facet.version
    var scalaVersion: String = "27"
    try {
      val vers = java.lang.Double.parseDouble(version.substring(0,3))
      if (vers > 2.79) scalaVersion = "28"
    } catch {
      case e: Exception => //nothing to do
    }
    val scalaTestVersion: String = if (scalaVersion == "27") "10" else "15"

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
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
        params.setWorkingDirectory(getWorkingDirectory)
        //params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
        val list = new java.util.ArrayList[String]

        val jarPathForClass = PathUtil.getJarPathForClass(classOf[ScalaTestRunConfiguration])
        val virtFile = VcsUtil.getVirtualFile(jarPathForClass)
        val rtJarPath = PathUtil.getJarPathForClass(classOf[JavaSpecsRunner])
        params.getClassPath.add(rtJarPath)

        params.getClassPath.addAllFiles(facet.files)

        params.getClassPath.add(getClassPath(module))


        params.setMainClass(mainClass(scalaTestVersion, scalaVersion))

        params.getProgramParametersList.add("-s")
        for (cl <- classes) params.getProgramParametersList.add(cl.getQualifiedName)

        params.getProgramParametersList.add("-r")
        params.getProgramParametersList.add(reporterClass(scalaTestVersion, scalaVersion))
        params.getProgramParametersList.addParametersString(testArgs)
        return params
      }


      override def execute(executor: Executor, runner: ProgramRunner[_ <: JDOMExternalizable]): ExecutionResult = {
        val processHandler = startProcess
        val config = ScalaTestRunConfiguration.this
        val consoleProperties = new SMTRunnerConsoleProperties(config, "Scala", executor);

        // console view
        val testRunnerConsole: BaseTestsOutputConsoleView = SMTestRunnerConnectionUtil.attachRunner("Scala", 
          processHandler, consoleProperties, getRunnerSettings.asInstanceOf[RunnerSettings[_ <: JDOMExternalizable]],
          getConfigurationSettings)

        new DefaultExecutionResult(testRunnerConsole, processHandler, createActions(testRunnerConsole, processHandler): _*)
      }
    }
    return state;
  }

  def getModule: Module = {
    getConfigurationModule.getModule
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new ScalaTestRunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaTestRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "path", getTestClassPath)
    JDOMExternalizer.write(element, "package", getTestPackagePath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "params", getTestArgs)
    JDOMExternalizer.write(element, "version", scalaTestVersion)
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    testClassPath = JDOMExternalizer.readString(element, "path")
    testPackagePath = JDOMExternalizer.readString(element, "package")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    testArgs = JDOMExternalizer.readString(element, "params")
    scalaTestVersion = JDOMExternalizer.readBoolean(element, "version")
    val pp = JDOMExternalizer.readString(element, "workingDirectory")
    if (pp != null) workingDirectory = pp
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