package org.jetbrains.plugins.scala.console.configuration

import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.PathsList
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.{ApiStatus, Nls}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.console.configuration.ScalaConsoleRunConfiguration.Scala2_13_2
import org.jetbrains.plugins.scala.console.configuration.ScalaSdkJLineFixer.{JlineResolveResult, showJLineMissingNotification}
import org.jetbrains.plugins.scala.console.{ScalaLanguageConsole, ScalaReplBundle}
import org.jetbrains.plugins.scala.project.*
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import java.io.File
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*
import scala.util.chaining.scalaUtilChainingOps

/**
 * Run configuration with a single purpose: run Scala REPL instance in a internal IDEA console.
 * <br>
 * The class is not intended to be reused/extended in other plugins.
 * If you want to reuse some of the class functionality, please contact Scala Plugin team
 * via https://gitter.im/JetBrains/intellij-scala and we will extract some proper abstract base class.
 */
@ApiStatus.Experimental
class ScalaConsoleRunConfiguration(
  project: Project,
  configurationFactory: ConfigurationFactory,
  name: String
) extends ModuleBasedConfiguration[RunConfigurationModule, Element](
  name,
  new RunConfigurationModule(project),
  configurationFactory
) {

  //language=Scala
  private val Scala2MainClass = "scala.tools.nsc.MainGenericRunner"
  //language=Scala
  private val Scala3MainClass = "dotty.tools.repl.Main"

  private val DefaultJavaOptions = ""
  private val UseJavaCp = "-usejavacp"

  @BeanProperty var myConsoleArgs: String = ""
  @BeanProperty var workingDirectory: String = Option(getProject.baseDir).map(_.getPath).getOrElse("")
  @BeanProperty var javaOptions: String = DefaultJavaOptions

  def consoleArgs: String = ensureUsesJavaCpByDefault(this.myConsoleArgs)

  def consoleArgs_=(s: String): Unit = this.myConsoleArgs = ensureUsesJavaCpByDefault(s)

  private def ensureUsesJavaCpByDefault(args: String): String =
    if (args == null || args.isEmpty) UseJavaCp
    else args

  private def getModule: Option[Module] = Option(getConfigurationModule.getModule)

  private def requireModule: Module = getModule.getOrElse(throw new ExecutionException(ScalaReplBundle.message("scala.console.config.module.is.not.specified")))

  override def getValidModules: java.util.List[Module] = getProject.modulesWithScala.toList.asJava

  def apply(params: ScalaConsoleRunConfigurationForm): Unit = {
    javaOptions = params.getJavaOptions
    consoleArgs = params.getConsoleArgs
    workingDirectory = params.getWorkingDirectory
    setModule(params.getModule)
  }

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] =
    new ScalaConsoleRunConfigurationEditor(project, this)

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    XmlSerializer.serializeInto(this, element)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    readModule(element)
    XmlSerializer.deserializeInto(this, element)
    migrate(element)
  }

  private def migrate(element: Element): Unit = JdomExternalizerMigrationHelper(element) { helper =>
    helper.migrateString("consoleArgs")(consoleArgs = _)
    helper.migrateString("workingDirectory")(workingDirectory = _)
    helper.migrateString("javaOptions")(javaOptions = _)
    // see revision 8a3f9d28c, some time ago javaOptions was serialized as "vmparams4"
    helper.migrateString("vmparams4")(javaOptions = _)
  }

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
    new ScalaCommandLineState(env)

  //overriding the method as a workaround for https://github.com/lampepfl/dotty/issues/19007
  override def clone(): ModuleBasedConfiguration[_ <: RunConfigurationModule, _] = super.clone()

  private class ScalaCommandLineState(env: ExecutionEnvironment) extends JavaCommandLineState(env) {
    getModule match {
      case Some(module) =>
        setConsoleBuilder(ScalaLanguageConsole.builderFor(module))
      case None =>
    }

    override protected def createJavaParameters: JavaParameters = {
      val params = createParams
      val module = requireModule
      params.getProgramParametersList.addParametersString(consoleArgs)

      //Without this option JLine won't be able to create a terminal
      //And in Scala 3 there is no way to disable JLine like in Scala so we just set it in "dumb" mode
      //see dotty.tools.repl.JLineTerminal.dumbTerminal (in scala3-compiler_3-3.0.0.jar)
      //Also see related: IDEA-183619
      if (module.hasScala3)
        params.addEnv("TERM", "dumb")
      else
        params.getProgramParametersList.addParametersString(disableJLineOption)

      params
    }

    override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
      val params: JavaParameters = getJavaParameters
      val classPath = params.getClassPath

      val module = requireModule
      val success = ensureJLineInClassPathOrShowErrorNotification(classPath, module, ScalaLanguageConsole.ScalaConsoleTitle)
      if (success)
        super.execute(executor, runner)
      else
        null
    }

    private def ensureJLineInClassPathOrShowErrorNotification(classPathList: PathsList, module: Module, @Nls subsystemName: String): Boolean = {
      val classPath = classPathList.getPathList.asScala.map(new File(_)).toSeq
      val result = ScalaSdkJLineFixer.validateJLineInClassPath(classPath, module)
      result match {
        case JlineResolveResult.NotRequired =>
          true
        case JlineResolveResult.RequiredFound(file) =>
          classPathList.add(file)
          true
        case JlineResolveResult.RequiredNotFound =>
          showJLineMissingNotification(module, subsystemName)
          false
      }
    }
  }

  private def disableJLineOption: String = {
    val scalaVersion: Option[ScalaVersion] = getModule.flatMap(_.scalaMinorVersion)
    if (scalaVersion.exists(_ >= Scala2_13_2))
      "-Xjline:off" // https://github.com/scala/scala/pull/8906
    else
      "-Xnojline"
  }

  private def createParams: JavaParameters = {
    val module = requireModule

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    new JavaParameters().tap { params =>
      params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)

      params.getVMParametersList.addParametersString(javaOptions)
      params.getClassPath.addScalaCompilerClassPath(module)
      params.setShortenCommandLine(getShortenCommandLineMethod(Option(params.getJdk)), project)
      params.getClassPath.addRunners()
      params.setWorkingDirectory(workingDirectory)

      val mainClass = if (module.hasScala3) Scala3MainClass else Scala2MainClass
      params.setMainClass(mainClass)
    }
  }

  /** ShortenCommandLine.ARGS_FILE is intentionally not used even if JdkUtil.useClasspathJar is true
   * Scala REPL does not work in JDK 8 with manifest classpath
   *
   * @see [[com.intellij.execution.ShortenCommandLine.getDefaultMethod]]
   */
  private def getShortenCommandLineMethod(jdk: Option[Sdk]): ShortenCommandLine =
    if (!JdkUtil.useDynamicClasspath(getProject)) {
      ShortenCommandLine.NONE
    } else if (safeMap(jdk)(_.getHomePath).exists(JdkUtil.isModularRuntime)) {
      ShortenCommandLine.ARGS_FILE
    } else {
      ShortenCommandLine.CLASSPATH_FILE
    }

  @inline
  private def safeMap[A, B](oa: Option[A])(f: A => B): Option[B] =
    oa.flatMap(a => Option(f(a)))
}

object ScalaConsoleRunConfiguration {
  private val Scala2_13_2 = ScalaVersion.Latest.Scala_2_13.withMinor(2)
}
