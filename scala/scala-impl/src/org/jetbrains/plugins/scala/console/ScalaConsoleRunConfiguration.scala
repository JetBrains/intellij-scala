package org.jetbrains.plugins.scala
package console


import com.intellij.execution.configurations.{ConfigurationFactory, JavaParameters, _}
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.{CantRunException, ExecutionException, ExecutionResult, Executor}
import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
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

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val state = new ScalaCommandLineState(env)
    state.setConsoleBuilder(new TextConsoleBuilderImpl(project) {
      override def getConsole: ConsoleView = ScalaLanguageConsoleBuilder.createConsole(project)
    })
    state
  }

  private class ScalaCommandLineState(env: ExecutionEnvironment) extends JavaCommandLineState(env) {
    protected override def createJavaParameters: JavaParameters = {
      val params = createParams
      params.getProgramParametersList.addParametersString(consoleArgs)
      params
    }

    override def execute(executor: Executor, runner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
      val params: JavaParameters = getJavaParameters
      if (ensureClassPathHasJLine(params.getClassPath, getModule)) {
        super.execute(executor, runner)
      } else {
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
    import JLineFinder.JLineJarName
    import ScalaLanguageConsoleView.ScalaConsole
    val message =
      s"""$ScalaConsole requires $JLineJarName to run
         |Please add it to the project classpath""".stripMargin.replaceAll("\n", "<br>")

    val action = new NotificationAction("&Configure project classpath") {
      override def startInTransaction: Boolean = true
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        val configurable = ProjectStructureConfigurable.getInstance(project)
        new SingleConfigurableEditor(project, configurable, SettingsDialog.DIMENSION_KEY) {
          override protected def getStyle = DialogStyle.COMPACT
        }.show()
        notification.expire()
      }
    }

    NotificationUtil.builder(project, message)
      .addAction(action)
      .setTitle(null)
      .show()
  }
}

object ScalaConsoleRunConfiguration {

  /**
   * This is a temporary workaround to make Scala Console run in Scala 2.13 version.
   * It will fail to run if jline jar is not present in classpath.
   * For the details, please see the discussion: [[https://youtrack.jetbrains.com/issue/SCL-15818]]
   * TODO: Fix Scala SDK setup in order that it includes jline jar as a dependency of scala-compiler
   *
   * @return false - if jline jar could not be found and it is required to run scala console in current scala version<br>
   *         true - otherwise
   */
  private def ensureClassPathHasJLine(classPath: PathsList, module: Module): Boolean = {
    (for {
      languageLevel <- module.scalaLanguageLevel
      if languageLevel >= ScalaLanguageLevel.Scala_2_13
      classPathJars = module.scalaCompilerClasspath
      if !classPathJars.exists(jar => jar.getName == JLineFinder.JLineJarName && jar.exists)
    } yield JLineFinder.locateJLineJar(classPathJars) match {
      case Some(jLineJar) =>
        classPath.add(jLineJar)
        true
      case _ =>
        false
    }).getOrElse(true)
  }
}
