package org.jetbrains.plugins.scala.console.configuration

import java.io.File

import com.intellij.execution._
import com.intellij.execution.configurations.{ConfigurationFactory, JavaParameters, _}
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.DialogWrapper.DialogStyle
import com.intellij.util.PathsList
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.console.configuration.ScalaConsoleRunConfiguration.JlineResolveResult._
import org.jetbrains.plugins.scala.console.configuration.ScalaConsoleRunConfiguration._
import org.jetbrains.plugins.scala.console.{ScalaLanguageConsole, ScalaLanguageConsoleView}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.{JdomExternalizerMigrationHelper, NotificationUtil}
import org.jetbrains.sbt.{RichFile, RichOption}

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._

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

  private val MainClass = "scala.tools.nsc.MainGenericRunner"
  private val DefaultJavaOptions = "-Djline.terminal=NONE"
  private val UseJavaCp = "-usejavacp"

  @BeanProperty var myConsoleArgs: String = ""
  @BeanProperty var workingDirectory: String = Option(getProject.baseDir).map(_.getPath).getOrElse("")
  @BeanProperty var javaOptions: String = DefaultJavaOptions

  def consoleArgs: String = ensureUsesJavaCpByDefault(this.myConsoleArgs)
  def consoleArgs_=(s: String): Unit = this.myConsoleArgs = ensureUsesJavaCpByDefault(s)

  private def ensureUsesJavaCpByDefault(s: String): String = if (s == null || s.isEmpty) UseJavaCp else s

  private def getModule: Option[Module] = Option(getConfigurationModule.getModule)

  private def requireModule: Module = getModule.getOrElse(throw new ExecutionException(ScalaBundle.message("scala.console.config.module.is.not.specified")))

  override protected def getValidModules: java.util.List[Module] = getProject.modulesWithScala.toList.asJava

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

  private class ScalaCommandLineState(env: ExecutionEnvironment) extends JavaCommandLineState(env) {
    getModule match {
      case Some(module) =>
        setConsoleBuilder(ScalaLanguageConsole.builderFor(module))
      case None =>
    }

    override protected def createJavaParameters: JavaParameters = {
      val params = createParams
      val args = consoleArgs + " " + disableJLineOption
      params.getProgramParametersList.addParametersString(args)
      params
    }

    override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
      val params: JavaParameters = getJavaParameters
      val classPath = params.getClassPath

      val module = requireModule
      validateJLineInClassPath(classPath, module) match {
        case JlineResolveResult.NotRequired =>
          super.execute(executor, runner)
        case RequiredFound(file) =>
          classPath.add(file)
          super.execute(executor, runner)
        case JlineResolveResult.RequiredNotFound =>
          showJLineMissingNotification(module)
          null
      }
    }
  }

  private def disableJLineOption: String =
    getModule.flatMap(minorScalaVersion) match {
      case Some(version) if version >= "2.13.2" => "-Xjline:off" // https://github.com/scala/scala/pull/8906
      case _                                    => "-Xnojline"
    }

  private def createParams: JavaParameters = {
    val module = requireModule

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    new JavaParameters {
      configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)

      getVMParametersList.addParametersString(javaOptions)
      getClassPath.addScalaClassPath(module)
      setShortenCommandLine(getShortenCommandLineMethod(Option(getJdk)), project)
      getClassPath.addRunners()
      setWorkingDirectory(workingDirectory)
      setMainClass(MainClass)
    }
  }

  /** ShortenCommandLine.ARGS_FILE is intentionally not used even if JdkUtil.useClasspathJar is true
   * Scala REPL does not work in JDK 8 with manifest classpath
   *
   * @see [[com.intellij.execution.ShortenCommandLine.getDefaultMethod]]
   */
  private def getShortenCommandLineMethod(jdk: Option[Sdk]): ShortenCommandLine =
    if(!JdkUtil.useDynamicClasspath(getProject)){
      ShortenCommandLine.NONE
    } else if(jdk.safeMap(_.getHomePath).exists(JdkUtil.isModularRuntime)) {
      ShortenCommandLine.ARGS_FILE
    } else {
      ShortenCommandLine.CLASSPATH_FILE
    }

  private def showJLineMissingNotification(module: Module): Unit = {
    val message: String = {
      import JLineFinder.JLineJarName
      import ScalaLanguageConsoleView.ScalaConsole
      val sdkName = module.scalaSdk.safeMap(_.getName).getOrElse(ScalaBundle.message("scala.console.config.unknown.sdk"))
      ScalaBundle.message("scala.console.config.scala.console.requires.jline", ScalaConsole, JLineJarName, sdkName).replaceAll("\n", "<br>")
    }

    val goToSdkSettingsAction = new NotificationAction(ScalaBundle.message("scala.console.configure.scala.sdk.classpath")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.expire()
        val configurable = ProjectStructureConfigurable.getInstance(project)
        val editor = new SingleConfigurableEditor(project, configurable, SettingsDialog.DIMENSION_KEY) {
          override protected def getStyle = DialogStyle.COMPACT
        }
        module.scalaSdk match {
          case Some(sdk) => configurable.selectProjectOrGlobalLibrary(sdk, true)
          case None      => configurable.selectGlobalLibraries(true)
        }
        editor.show()
      }
    }

    NotificationUtil.builder(project, message)
      .addAction(goToSdkSettingsAction)
      .setTitle(null)
      .show()
  }
}

private object ScalaConsoleRunConfiguration {

  sealed trait JlineResolveResult
  object JlineResolveResult {
    case object NotRequired extends JlineResolveResult
    case object RequiredNotFound extends JlineResolveResult
    case class RequiredFound(file: File) extends JlineResolveResult
  }

  //TODO: Fix Scala SDK setup in order that it includes jline jar as a dependency of scala-compiler
  /**
   * This is a workaround to make Scala Console run in Scala 2.13.0 & 2.13.1 versions
   * It will fail to run if jline jar is not present in classpath.
   * For the details, please see the discussion: [[https://youtrack.jetbrains.com/issue/SCL-15818]]
   *
   * @return false - if jline jar could not be found and it is required to run scala console in current scala version<br>
   *         true - otherwise
   */
  private def validateJLineInClassPath(classPath: PathsList, module: Module): JlineResolveResult =
    if (isJLineNeeded(module) && !isJLinePresentIn(classPath)) {
      jLineFor(classPath) match {
        case Some(jline) => RequiredFound(jline)
        case None => RequiredNotFound
      }
    } else NotRequired

  private def isJLineNeeded(module: Module): Boolean =
    module.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_13) && {
      // 2.13.2 was fixed and does not require jline jar if jline is disabled
      // see https://github.com/scala/bug/issues/11654
      minorScalaVersion(module).exists(v => v == "2.13.0" || v == "2.13.1")
    }

  private def minorScalaVersion(module: Module): Option[String] =
    module.scalaSdk.flatMap(_.compilerVersion)

  private def isJLinePresentIn(classPath: PathsList): Boolean =
    classPath.getPathList.asScala.exists(new File(_).getName == JLineFinder.JLineJarName)

  private def jLineFor(classPath: PathsList): Option[File] = {
    val jars = classPath.getPathList.asScala.map(new File(_))
    val compilerJar = jars.find(_.getName.startsWith("scala-compiler"))
    compilerJar.flatMap(JLineFinder.findJline)
  }

  private object JLineFinder {
    //this is a dependency of scala-compiler-2.13.0 & 2.13.1, it is the last jline 2.x version
    //so we can use exact value instead of some regexp with versions
    //see: https://mvnrepository.com/artifact/org.scala-lang/scala-compiler/2.13.0
    //see: https://mvnrepository.com/artifact/jline/jline
    //see: https://github.com/jline/jline2
    private val JLineVersionInScala213 = "2.14.6"
    val JLineJarName = s"jline-$JLineVersionInScala213.jar"

    def findJline(compilerJar: File): Option[File] =
      findInSameFolder(compilerJar)
        .orElse(findInIvy(compilerJar))
        .orElse(findInMaven(compilerJar))

    private def findInSameFolder(compilerJar: File): Option[File] = for {
      parent <- compilerJar.parent
      jLineJar <- (parent / JLineJarName).maybeFile
    } yield jLineJar

    //location of `scala-compiler-x.x.x.jar` : .ivy2/cache/org.scala-lang/scala-compiler/jars
    //location of `jline-x.x.x.jar`          : .ivy2/cache/jline/jline/jars
    private def findInIvy(compilerJar: File): Option[File] = for {
      cacheFolder <- compilerJar.parent(level = 4)
      jLineFolder <- (cacheFolder / "jline" / "jline" / "jars").maybeDir
      jLineJar <- (jLineFolder / JLineJarName).maybeFile
    } yield jLineJar

    //location of `scala-compiler-x.x.x.jar` : .m2/repository/org/scala-lang/scala-compiler/x.x.x
    //location of `jline-x.x.x.jar`          : .m2/repository/jline/jline/x.x.x
    private def findInMaven(compilerJar: File): Option[File] = for {
      repositoryFolder <- compilerJar.parent(level = 5)
      jLineFolder <- (repositoryFolder / "jline" / "jline" / JLineVersionInScala213).maybeDir
      jLineJar <- (jLineFolder / JLineJarName).maybeFile
    } yield jLineJar
  }
}