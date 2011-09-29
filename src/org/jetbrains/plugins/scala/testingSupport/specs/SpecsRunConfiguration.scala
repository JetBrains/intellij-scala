package org
package jetbrains
package plugins
package scala
package testingSupport
package specs

import _root_.java.io.File
import _root_.scala.collection.mutable.ArrayBuffer
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
import compiler.rt.ScalacRunner
import jdom.Element
import _root_.scala.collection.mutable.HashSet
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.execution.configurations._
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import reflect.BeanProperty
import lang.psi.impl.ScPackageImpl
import config.ScalaFacet
import collection.JavaConversions._
import lang.psi.ScalaPsiUtil

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
  val MAIN_CLASS_28 = "org.jetbrains.plugins.scala.testingSupport.specs.JavaSpecsRunner"
  def SUITE_PATH: String = "org.specs.Specification"

  private var testClassPath = ""
  private var testPackagePath = ""
  private var testArgs = ""
  private var javaOptions = ""
  @BeanProperty
  var workingDirectory = {
    val base = getProject.getBaseDir
    if (base != null) base.getPath
    else ""
  }
  var sysFilter = ".*"
  var exampleFilter = ".*"

  def getTestClassPath = testClassPath
  def getTestPackagePath = testPackagePath
  def getTestArgs = testArgs
  def getJavaOptions = javaOptions
  def getSystemFilter = sysFilter
  def getExampleFilter = exampleFilter
  def setTestClassPath(s: String) {testClassPath = s}
  def setTestPackagePath(s: String) {testPackagePath = s}
  def setTestArgs(s: String) {testArgs = s}
  def setJavaOptions(s: String) {javaOptions = s}
  def setSystemFilter(s: String) {sysFilter = s}
  def setExampleFilter(s: String) {exampleFilter = s}

  private var generatedName: String = ""
  override def getGeneratedName = generatedName
  def setGeneratedName(name: String) {generatedName = name}
  override def isGeneratedName = getName == null || getName.equals(suggestedName)
  override def suggestedName = getGeneratedName

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
    setSystemFilter(configuration.getSystemFilter)
    setExampleFilter(configuration.getExampleFilter)
    workingDirectory = configuration.getWorkingDirectory
  }

  def getClazz(path: String): PsiClass = {
    val facade = JavaPsiFacade.getInstance(project)
    facade.findClass(path, GlobalSearchScope.allScope(project))
  }

  def getClazzes(path: String): Array[PsiClass] = {
    val facade = JavaPsiFacade.getInstance(project)
    facade.findClasses(path, GlobalSearchScope.allScope(project))
  }

  def getPackage(path: String): PsiPackage = {
    ScPackageImpl.findPackage(project, path)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    def throwClassNotFoundError() {
      throw new ExecutionException("Test class not found.")
    }
    var classOrObjects: Array[PsiClass] = Array()
    var suiteClass: PsiClass = null
    var pack: PsiPackage = null
    try {
      if (testClassPath != "")
        classOrObjects = getClazzes(testClassPath)
      else
        pack = getPackage(testPackagePath)
      suiteClass = getClazz(SUITE_PATH)
    }
    catch {
      case e => throwClassNotFoundError()
    }
    if (classOrObjects.isEmpty && pack == null) throwClassNotFoundError()
    if (suiteClass == null)
      throw new ExecutionException("Specs not specified.")
    val classes = new ArrayBuffer[PsiClass]
    if (!classOrObjects.isEmpty) {
      classes ++= classOrObjects.filter(ScalaPsiUtil.cachedDeepIsInheritor(_, suiteClass))
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

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !(sdk.getSdkType.isInstanceOf[JavaSdkType])) {
      throw CantRunException.noJdkForModule(module)
    }
    val sdkType = sdk.getSdkType

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters()

        params.setJdk(sdk)

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        //params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
        val list = new java.util.ArrayList[String]
        params.setWorkingDirectory(workingDirectory)

        val rtJarPath = PathUtil.getJarPathForClass(classOf[ScalacRunner])
        params.getClassPath.add(rtJarPath)

        params.getClassPath.addAllFiles(facet.files)

        params.getClassPath.add(getClassPath(module))


        params.setMainClass(
          if (scalaVersion == "27") MAIN_CLASS
          else MAIN_CLASS_28
        )

        params.getProgramParametersList.add("-s")
        for (cl <- classes) params.getProgramParametersList.add(cl.getQualifiedName)
        params.getProgramParametersList.add("-sus:" + getSystemFilter)
        params.getProgramParametersList.add("-ex:" + getExampleFilter)
        return params
      }


      override def execute(executor: Executor, runner: ProgramRunner[_ <: JDOMExternalizable]): ExecutionResult = {
        val processHandler = startProcess()
        val config = SpecsRunConfiguration.this
        val consoleProperties = new SMTRunnerConsoleProperties(config, "Scala", executor);

        // console view
        val testRunnerConsole: BaseTestsOutputConsoleView = SMTestRunnerConnectionUtil.attachRunner("Scala", 
          processHandler, consoleProperties, getRunnerSettings.asInstanceOf[RunnerSettings[_ <: JDOMExternalizable]],
          getConfigurationSettings)

        new DefaultExecutionResult(testRunnerConsole, processHandler,
          createActions(testRunnerConsole, processHandler): _*)
      }
    }
    return state;
  }

  def getModule: Module = {
    getConfigurationModule.getModule
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new SpecsRunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new SpecsRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "path", getTestClassPath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "params", getTestArgs)
    JDOMExternalizer.write(element, "packagepath", getTestPackagePath)
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
    JDOMExternalizer.write(element, "sysFilter", sysFilter)
    JDOMExternalizer.write(element, "exampleFilter", exampleFilter)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    testClassPath = JDOMExternalizer.readString(element, "path")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    testArgs = JDOMExternalizer.readString(element, "params")
    //todo: this var should be removed after few releases. Creation date: 08.04.2010
    var pp = JDOMExternalizer.readString(element, "packagepath")
    if (pp != null) testPackagePath = pp
    pp = JDOMExternalizer.readString(element, "workingDirectory")
    if (pp != null) workingDirectory = pp
    pp = JDOMExternalizer.readString(element, "sysFilter")
    if (pp != null) sysFilter = pp
    pp = JDOMExternalizer.readString(element, "exampleFilter")
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
    return res.toString()
  }
}