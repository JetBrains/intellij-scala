package org.jetbrains.plugins.scala.console.configuration

import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.DialogWrapper.DialogStyle
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.console.ScalaReplBundle
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.plugins.scala.project.*
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

import java.io.File

//TODO: Fix Scala SDK setup in order that it includes jline jar as a dependency of scala-compiler
/**
 * This is a workaround to make Scala Console & Worksheets run in Scala 2.13.0 & 2.13.1 versions.<br>
 * It will fail to run if jline jar is not present in classpath.<br>
 * For the details, please see the discussion: [[https://youtrack.jetbrains.com/issue/SCL-15818]]
 */
object ScalaSdkJLineFixer {

  sealed trait JlineResolveResult
  object JlineResolveResult {
    case object NotRequired extends JlineResolveResult
    case object RequiredNotFound extends JlineResolveResult
    case class RequiredFound(file: File) extends JlineResolveResult
  }

  import JlineResolveResult.*

  /**
   * @return false - if jline jar could not be found and it is required to run scala console in current scala version<br>
   *         true - otherwise
   */
  def validateJLineInClassPath(classPath: Seq[File], module: Module): JlineResolveResult =
    if (isJLineNeeded(module) && !isJLinePresentIn(classPath))
      jLineFor(classPath) match {
        case Some(jline) => RequiredFound(jline)
        case None        => RequiredNotFound
      }
    else NotRequired

  def validateJLineInCompilerClassPath(module: Module): JlineResolveResult = {
    val classPath = module.scalaCompilerClasspath
    validateJLineInClassPath(classPath, module)
  }

  def showJLineMissingNotification(module: Module, @Nls subsystemName: String): Unit = {
    val project = module.getProject

    val message: String =
      ScalaReplBundle.message("subsystem.requires.jline", subsystemName, JLineFinder.JLineJarName).replaceAll("\n", "<br>")

    val goToSdkSettingsAction = new NotificationAction(ScalaReplBundle.message("scala.console.configure.scala.sdk.classpath")) {
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

    ScalaNotificationGroups.scalaGeneral
      .createNotification(message, NotificationType.WARNING)
      .addAction(goToSdkSettingsAction)
      .notify(project)
  }


  private def isJLineNeeded(module: Module): Boolean =
    module.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_13) && {
      // 2.13.2 was fixed and does not require jline jar if jline is disabled
      // see https://github.com/scala/bug/issues/11654
      module.scalaMinorVersion.map(_.minor).exists(v => v == "2.13.0" || v == "2.13.1")
    }

  private def isJLinePresentIn(classPath: Seq[File]): Boolean =
    classPath.exists(_.getName == JLineFinder.JLineJarName)

  private def jLineFor(classPath: Seq[File]): Option[File] = {
    val compilerJar = classPath.find(_.getName.startsWith("scala-compiler"))
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
