package org.jetbrains.plugins.scala
package console

import java.io.File

import org.jetbrains.sbt.RichFile
import java.io.File

import com.intellij.execution.configurations.{ConfigurationFactory, JavaParameters, _}
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.{CantRunException, ExecutionException, ExecutionResult, Executor}
import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.DialogWrapper.DialogStyle
import com.intellij.util.PathsList
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.plugins.scala.console.ScalaConsoleRunConfiguration.JlineResolveResult.{NotRequired, RequiredFound, RequiredNotFound}
import org.jetbrains.plugins.scala.console.ScalaConsoleRunConfiguration._
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.NotificationUtil

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

class ScalaConsoleRunConfiguration(project: Project, configurationFactory: ConfigurationFactory, name: String)
  extends ModuleBasedConfiguration[RunConfigurationModule, Element](name, new RunConfigurationModule(project), configurationFactory) {

  private val MainClass = "scala.tools.nsc.MainGenericRunner"
  private val DefaultJavaOptions = "-Djline.terminal=NONE"
  private val UseJavaCp = "-usejavacp"

  @BeanProperty var myConsoleArgs: String = ""
  @BeanProperty var workingDirectory: String = Option(getProject.baseDir).map(_.getPath).getOrElse("")
  @BeanProperty var javaOptions: String = DefaultJavaOptions

  def consoleArgs: String = ensureUsesJavaCpByDefault(this.myConsoleArgs)
  def consoleArgs_=(s: String): Unit = this.myConsoleArgs = ensureUsesJavaCpByDefault(s)

  private def ensureUsesJavaCpByDefault(s: String): String = if (s == null || s.isEmpty) UseJavaCp else s

  private def getModule: Module = getConfigurationModule.getModule

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
  }

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
    new ScalaCommandLineState(env)

  private class ScalaCommandLineState(env: ExecutionEnvironment) extends JavaCommandLineState(env) {
    setConsoleBuilder(new TextConsoleBuilderImpl(project) {
      override def getConsole: ConsoleView = ScalaLanguageConsoleBuilder.createConsole(project)
    })

    protected override def createJavaParameters: JavaParameters = {
      val params = createParams
      params.getProgramParametersList.addParametersString(consoleArgs)
      params
    }

    override def execute(executor: Executor, runner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
      val params: JavaParameters = getJavaParameters
      val classPath = params.getClassPath

      validateJLineInClassPath(classPath, getModule) match {
        case JlineResolveResult.NotRequired =>
          super.execute(executor, runner)
        case RequiredFound(file) =>
          classPath.add(file)
          super.execute(executor, runner)
        case JlineResolveResult.RequiredNotFound =>
          showJLineMissingNotification()
          null
      }
    }
  }

  private def createParams: JavaParameters = {
    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    new JavaParameters() {
      getVMParametersList.addParametersString(javaOptions)
      getClassPath.addScalaClassPath(module)
      setUseDynamicClasspath(JdkUtil.useDynamicClasspath(getProject))
      getClassPath.addRunners()
      setWorkingDirectory(workingDirectory)
      setMainClass(MainClass)
      configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
    }
  }

  private def showJLineMissingNotification(): Unit = {
    val message = {
      import JLineFinder.JLineJarName
      import ScalaLanguageConsoleView.ScalaConsole
      val sdkName = getModule.scalaSdk.map(_.getName).getOrElse("")
      s"""$ScalaConsole requires $JLineJarName to run
         |Please add it to `$sdkName` compiler classpath
         |""".stripMargin.trim.replaceAll("\n", "<br>")
    }

    val action = new NotificationAction("&Configure Scala SDK classpath") {
      override def startInTransaction: Boolean = true
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.expire()
        val configurable = ProjectStructureConfigurable.getInstance(project)
        new SingleConfigurableEditor(project, configurable, SettingsDialog.DIMENSION_KEY) {
          override protected def getStyle = DialogStyle.COMPACT
        }.show()
        configurable.selectGlobalLibraries(true)
      }
    }

    NotificationUtil.builder(project, message)
      .addAction(action)
      .setTitle(null)
      .show()
  }
}

private object ScalaConsoleRunConfiguration {
  sealed trait JlineResolveResult
  object JlineResolveResult {
    case object NotRequired extends JlineResolveResult
    case object RequiredNotFound extends JlineResolveResult
    case class  RequiredFound(file: File) extends JlineResolveResult
  }

  //TODO: Fix Scala SDK setup in order that it includes jline jar as a dependency of scala-compiler
  /**
   * This is a temporary workaround to make Scala Console run in Scala 2.13 version.
   * It will fail to run if jline jar is not present in classpath.
   * For the details, please see the discussion: [[https://youtrack.jetbrains.com/issue/SCL-15818]]
   *
   * @return false - if jline jar could not be found and it is required to run scala console in current scala version<br>
   *         true - otherwise
   */
  def validateJLineInClassPath(classPath: PathsList, module: Module): JlineResolveResult =
    if (isJLineNeeded(module) && !isJLinePresentIn(classPath)) {
      jLineFor(classPath) match {
        case Some(jline) => RequiredFound(jline)
        case None => RequiredNotFound
      }
    } else NotRequired

  def isJLineNeeded(module: Module): Boolean =
    module.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_13)

  def isJLinePresentIn(classPath: PathsList): Boolean =
    classPath.getPathList.asScala.contains(JLineFinder.JLineJarName)

  def jLineFor(classPath: PathsList): Option[File] = {
    val jars = classPath.getPathList.asScala.map(new File(_))
    val compilerJar = jars.find(_.getName.startsWith("scala-compiler"))
    compilerJar.flatMap(JLineFinder.findJline)
  }

  object JLineFinder {
    //this is a dependency of scala-compiler-2.13.0, it is the last jline 2.x version
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