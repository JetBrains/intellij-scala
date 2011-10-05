package org.jetbrains.plugins.scala
package testingSupport
package scalaTest


import java.io.File
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
import collection.mutable.HashSet
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.execution.configurations._
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}

import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
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
        params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)

        params.setMainClass(mainClass)

        params.getProgramParametersList.add("-s")
        for (cl <- classes) params.getProgramParametersList.add(cl.getQualifiedName)

        params.getProgramParametersList.add("-r")
        params.getProgramParametersList.add(reporterClass)
        params.getProgramParametersList.addParametersString(testArgs)
        params
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
    state;
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
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    testClassPath = JDOMExternalizer.readString(element, "path")
    testPackagePath = JDOMExternalizer.readString(element, "package")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    testArgs = JDOMExternalizer.readString(element, "params")
    val pp = JDOMExternalizer.readString(element, "workingDirectory")
    if (pp != null) workingDirectory = pp
  }
}