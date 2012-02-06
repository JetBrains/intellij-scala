package org.jetbrains.plugins.scala
package testingSupport
package specs2

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
import compiler.rt.ClassRunner
import _root_.scala.collection.mutable.HashSet
import com.intellij.openapi.module.Module
import com.intellij.execution.configurations._
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import reflect.BeanProperty
import config.ScalaFacet
import org.jdom.Element
import scala.collection.JavaConversions._
import lang.psi.ScalaPsiUtil
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.options.{SettingsEditorGroup, SettingsEditor}
import com.intellij.diagnostic.logging.LogConfigurationPanel
import extensions.toPsiClassExt
import lang.psi.impl.{ScalaPsiManager, ScPackageImpl}

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */


class Specs2RunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project),
          configurationFactory) with ScalaTestingConfiguration {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS_SPECS_2 = "org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Runner"
  def SUITE_PATH: String = "org.specs2.specification.SpecificationStructure"

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

  def getTestClassPath = testClassPath
  def getTestPackagePath = testPackagePath
  def getTestArgs = testArgs
  def getJavaOptions = javaOptions
  def setTestClassPath(s: String) {testClassPath = s}
  def setTestPackagePath(s: String) {testPackagePath = s}
  def setTestArgs(s: String) {testArgs = s}
  def setJavaOptions(s: String) {javaOptions = s}

  private var generatedName: String = ""
  override def getGeneratedName = generatedName
  def setGeneratedName(name: String) {generatedName = name}
  override def isGeneratedName = getName == null || getName.equals(suggestedName)
  override def suggestedName = getGeneratedName

  def apply(configuration: Specs2RunConfigurationForm) {
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
    workingDirectory = configuration.getWorkingDirectory
  }

  def getClazz(path: String): PsiClass = {
    ScalaPsiManager.instance(getProject).getCachedClass(GlobalSearchScope.allScope(project), path)
  }

  def getClazzes(path: String): Array[PsiClass] = {
    ScalaPsiManager.instance(getProject).getCachedClasses(GlobalSearchScope.allScope(project), path)
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
      throw new ExecutionException("Specs2 not specified.")
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

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters()

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        //params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
        params.setWorkingDirectory(workingDirectory)

        val rtJarPath = PathUtil.getJarPathForClass(classOf[ClassRunner])
        params.getClassPath.add(rtJarPath)
        params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)

        params.setMainClass(MAIN_CLASS_SPECS_2)

        val programParams = params.getProgramParametersList
        for (cl <- classes) {
          programParams.add(cl.qualifiedName)
        }
        programParams.add("--")
        programParams.addParametersString(getTestArgs)
        for (ext <- Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
          ext.updateJavaParameters(Specs2RunConfiguration.this, params, getRunnerSettings)
        }

        params
      }


      override def execute(executor: Executor, runner: ProgramRunner[_ <: JDOMExternalizable]): ExecutionResult = {
        val processHandler = startProcess()
        val config = Specs2RunConfiguration.this
        val runnerSettings = getRunnerSettings
        JavaRunConfigurationExtensionManager.getInstance.attachExtensionsToProcess(config, processHandler, runnerSettings)
        val consoleProperties = new SMTRunnerConsoleProperties(config, "Scala", executor);

        // console view
        val testRunnerConsole: BaseTestsOutputConsoleView = SMTestRunnerConnectionUtil.createAndAttachConsole("Scala",
          processHandler, consoleProperties, runnerSettings,
          getConfigurationSettings)

        new DefaultExecutionResult(testRunnerConsole, processHandler,
          createActions(testRunnerConsole, processHandler): _*)
      }
    }
    state;
  }

  def getModule: Module = {
    getConfigurationModule.getModule
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new Specs2RunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = {
    val group: SettingsEditorGroup[Specs2RunConfiguration] = new SettingsEditorGroup
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new Specs2RunConfigurationEditor(project, this))
    JavaRunConfigurationExtensionManager.getInstance.appendEditors(this, group)
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel)
    group
  }

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.writeExternal(this, element)
    writeModule(element)
    JDOMExternalizer.write(element, "path", getTestClassPath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "params", getTestArgs)
    JDOMExternalizer.write(element, "packagepath", getTestPackagePath)
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    JavaRunConfigurationExtensionManager.getInstance.writeExternal(this, element)
    readModule(element)
    testClassPath = JDOMExternalizer.readString(element, "path")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    testArgs = JDOMExternalizer.readString(element, "params")
    testPackagePath = JDOMExternalizer.readString(element, "packagepath")
    workingDirectory = JDOMExternalizer.readString(element, "workingDirectory")
  }
}