package org.jetbrains.plugins.scala
package worksheet
package ammonite

import java.io.File

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress._
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.{ModuleRootManager, OrderRootType}
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.project.template._
import org.jetbrains.plugins.scala.project.{Version, Versions}
import org.jetbrains.plugins.scala.util.{NotificationUtil, ScalaUtil}

import scala.collection.mutable
import scala.util.{Success, Try}

/**
 * User: Dmitry.Naydanov
 * Date: 17.01.18.
 */
object ImportAmmoniteDependenciesFix {

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.worksheet.ammonite.ImportAmmoniteDependenciesFix")

  private val DEFAULT_SCALA_VERSION = "2.12.4"
  private val DEFAULT_AMMONITE_VERSION = "1.0.3"

  private val AMMONITE_PREFIX = "ammonite_"
  private val THREE_DIGIT_PATTERN = "(\\d+\\.\\d+\\.\\d+)"

  def apply(file: ScFile)
           (implicit project: Project): Unit = {
    val manager = ProgressManager.getInstance

    val task = new Task.Backgroundable(project, ScalaBundle.message("ammonite.adding.dependencies.title"), false) {

      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setText(ScalaBundle.message("ammonite.loading.list.of.versions"))

        val (forScala, predicate) = ScalaUtil.getScalaVersion(file)
          .fold(
            (
              MajorVersion('2'): MyScalaVersion,
              Function.const(true)(_: File)
            )
          ) { version =>
            (
              ExactVersion(version.charAt(3), Version(version)),
              (file: File) => file.getName.startsWith("scala-") && Artifact.ScalaArtifacts.exists(_.versionOf(file).isDefined)
            )
          }

        val (scalaVersion, ammoniteVersion) = detectAmmoniteVersion(forScala)

        indicator.setText(ScalaBundle.message("ammonite.extracting.info.from.sbt"))

        val files = mutable.ListBuffer.empty[File]
        val e = new AmmoniteUtil.RegexExtractor
        import e._

        createTempSbtProject(
          scalaVersion,
          Seq("set libraryDependencies += \"com.lihaoyi\" " + "%" + " \"ammonite\" " + "%" + " \"" + ammoniteVersion + "\" " + "%" + " \"test\" cross CrossVersion.full"),
          Seq("show test:dependencyClasspath")
        ) {
          case mre"[info] * Attributed($pathname)" =>
            new File(pathname) match {
              case f if f.exists() => files += f
              case _ =>
            }
          case mre"[success]$_" =>
            indicator.setText(ScalaBundle.message("ammonite.adding.dependencies.progress"))

            ScalaUtil.getModuleForFile(file.getVirtualFile).foreach { module =>
              invokeLater {
                inWriteAction {
                  val tableModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getModifiableModel
                  val moduleModel = ModuleRootManager.getInstance(module).getModifiableModel
                  val jarFileSystem = JarFileSystem.getInstance

                  for {
                    file <- files
                    if predicate(file)

                    rootFile = jarFileSystem.findLocalVirtualFileByPath(file.getCanonicalPath)
                    if rootFile != null

                    library = tableModel.createLibrary(file.getName)
                    model = library.getModifiableModel
                  } {
                    model.addRoot(rootFile, OrderRootType.CLASSES)
                    model.commit()
                    moduleModel.addLibraryEntry(library)
                  }

                  tableModel.commit()
                  moduleModel.commit()
                }
              }
            }
          case mre"[error]$content" => LOG.warn(s"Ammonite, error while importing dependencies: $content")
          case _ =>
        }
      }
    }

    manager.runProcessWithProgressAsynchronously(task, createBgIndicator)
  }

  trait MyScalaVersion

  case class MajorVersion(m: Char) extends MyScalaVersion // m is last num in major version, e.g. 1 for 2.11
  case class ExactVersion(m: Char, v: Version) extends MyScalaVersion

  private def hasAmmonite(file: PsiFile): Boolean =
    LibraryTablesRegistrar.getInstance()
      .getLibraryTable(file.getProject)
      .getLibraries
      .exists(_.getName.startsWith("ammonite-"))

  private def createBgIndicator(implicit project: Project) =
    ProgressIndicatorProvider.getGlobalProgressIndicator match {
      case null => new BackgroundableProcessIndicator(
        project,
        ScalaBundle.message("ammonite.config.display.name"),
        PerformInBackgroundOption.ALWAYS_BACKGROUND,
        null,
        null,
        false
      )
      case indicator => indicator
    }

  private def detectAmmoniteVersion(forScala: MyScalaVersion): (String, String) = {
    val scalaVersion = loadScalaVersions(forScala) match {
      case Success(Some(v)) => v.presentation
      case _ => DEFAULT_SCALA_VERSION
    }

    val ammoniteVersion = loadAmmoniteVersion(forScala, scalaVersion).getOrElse(DEFAULT_AMMONITE_VERSION)

    (scalaVersion, ammoniteVersion)
  }

  def loadAmmoniteVersion(forScala: MyScalaVersion, scalaVersion: String): Try[String] = {
    Versions.loadLinesFrom(s"https://repo1.maven.org/maven2/com/lihaoyi/$AMMONITE_PREFIX$scalaVersion/").map {
      lines =>
        val pattern = ("\\Q\"\\E" + THREE_DIGIT_PATTERN).r

        lines.flatMap {
          line => pattern findFirstIn line
        }.map(str => Version(str stripPrefix "\""))
    }.filter(_.nonEmpty).map(_.max.presentation)
  }

  def loadScalaVersions(forScala: MyScalaVersion): Try[Option[Version]] = {
    Versions.loadLinesFrom("https://repo1.maven.org/maven2/com/lihaoyi/").map {
      lines =>
        val pattern = s"\\Q$AMMONITE_PREFIX\\E$THREE_DIGIT_PATTERN".r
        lines.flatMap(line => pattern.findFirstIn(line))
    }.map {
      ammoniteStrings =>
        val pattern = THREE_DIGIT_PATTERN.r
        ammoniteStrings.flatMap(v => pattern findFirstIn v).map(Version(_))
    }.map {
      ammoniteVersions => ammoniteVersions.groupBy(_.major(2).presentation.last)
    }.map {
      grouped =>
        forScala match {
          case MajorVersion(m) => grouped.get(m).map(_.last)
          case ExactVersion(m, v) =>
            grouped.get(m).flatMap {
              f => f.reverse.find(v >= _)
            }
        }
    }
  }

  def suggestAddingAmmonite(file: ScFile): Unit = {
    if (hasAmmonite(file)) return
    implicit val project: Project = file.getProject

    //noinspection ScalaExtractStringToBundle
    @Nls
    val ammoniteName = "Ammonite"

    NotificationUtil.showMessage(
      project = project,
      title = ammoniteName,
      message =
        ScalaBundle.message("add.a.all.ammonite.deps.to.project"),
      handler = {
        case "run" => apply(file)
        case "disable" => AmmoniteScriptWrappersHolder.getInstance(project).setIgnoreImports()
        case _ =>
      }
    )
  }
}