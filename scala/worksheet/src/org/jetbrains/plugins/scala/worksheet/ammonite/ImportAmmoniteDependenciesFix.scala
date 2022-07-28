package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress._
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.{ModuleRootManager, OrderRootType}
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.{inWriteAction, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.project.template.Artifact
import org.jetbrains.plugins.scala.project.{Version, Versions}
import org.jetbrains.plugins.scala.util.{NotificationUtil, ScalaUtil}
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle

import java.io.File
import scala.collection.mutable
import scala.util.{Success, Try}

object ImportAmmoniteDependenciesFix {

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.worksheet.ammonite.ImportAmmoniteDependenciesFix")

  // TODO: update versions
  private val DEFAULT_SCALA_VERSION = "2.12.4"
  private val DEFAULT_AMMONITE_VERSION = "1.0.3"

  private val AMMONITE_PREFIX = "ammonite_"

  private val THREE_DIGIT_PATTERN = """(\d+\.\d+\.\d+)"""

  // Examples: https://repo1.maven.org/maven2/com/lihaoyi/ammonite_2.13.3/
  // example1: 2.3.8
  // example2: 2.3.8-36-1cce53f3
  // the pattern search inside title="..." attribute, e.g. in:
  // <a href="2.1.4-11-307f3d8/" title="2.1.4-11-307f3d8/">2.1.4-11-307f3d8/</a>
  private val AMMONITE_VERSION_PATTERN = """"(\d+\.\d+\.\d+)(-\d+-[0-9a-f]+)?/?"""".r

  def apply(file: ScFile)
           (implicit project: Project): Unit = {
    val manager = ProgressManager.getInstance

    val task = new Task.Backgroundable(project, WorksheetBundle.message("ammonite.adding.dependencies.title"), false) {

      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setText(WorksheetBundle.message("ammonite.loading.list.of.versions"))

        val sv = ScalaUtil.getScalaVersion(file)
        val (forScala, predicate) = sv
          .fold(
            (
              MajorVersion('2'): MyScalaVersion,
              Function.const(true)(_: File)
            )
          ) { version =>
            (
              ExactVersion(version.charAt(3), Version(version)),
              (file: File) => !isScalaSdkFile(file)
            )
          }

        val (scalaVersion, ammoniteVersion) = detectAmmoniteVersion(forScala)

        indicator.setText(WorksheetBundle.message("ammonite.extracting.info.from.sbt"))

        val files = mutable.ListBuffer.empty[File]
        val e = new AmmoniteUtil.RegexExtractor
        import e._

        SbtUtils.createTempSbtProject(
          scalaVersion,
          Seq(s"""set libraryDependencies += "com.lihaoyi" % "ammonite" % "$ammoniteVersion" % "test" cross CrossVersion.full"""),
          Seq("""show test:dependencyClasspath""")
        ) {
          case mre"[info] * Attributed($pathname)" =>
            new File(pathname) match {
              case f if f.exists() => files += f
              case _ =>
            }
          case mre"[success]$_" =>
            indicator.setText(WorksheetBundle.message("ammonite.adding.dependencies.progress"))

            ScalaUtil.getModuleForFile(file.getVirtualFile).foreach { module =>
              invokeLater {
                inWriteAction {
                  val tableModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getModifiableModel
                  val moduleModel = ModuleRootManager.getInstance(module).getModifiableModel
                  val jarFileSystem = JarFileSystem.getInstance

                  val filesFiltered = files.filter(predicate)
                  for {
                    file <- filesFiltered

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
          case mre"[error]$_" => {
            // ignore, assuming that sbt return code will be not 0 and all the process output will be logged in
            // createTempSbtProject
          }
          case _ =>
        }
      }
    }

    manager.runProcessWithProgressAsynchronously(task, createBgIndicator)
  }

  // TODO: we should improve this filtering,
  //  currently in addition to ScalaLibrary it filters out
  //  ScalaCompiler,
  //  ScalaReflect,
  //  ScalaXml,
  //  ScalaSwing,
  //  ScalaCombinators,
  //  ScalaActors
  //  But what if a user wants to experiment with those libraries in ammonite script?
  private def isScalaSdkFile(file: File): Boolean =
    file.getName.startsWith("scala-") && Artifact.ScalaArtifacts.exists(_.versionOf(file).isDefined)

  // TODO: this is a bad solution:
  //  1: it's unreadable and strang
  //  2: it is incorrect, we can have same digits for scala 2 and scala 3, e.g. 0 can mean 2.10 and 3.0
  trait MyScalaVersion
  case class MajorVersion(m: Char) extends MyScalaVersion // m is last num in major version, e.g. 1 for 2.11
  case class ExactVersion(m: Char, v: Version) extends MyScalaVersion

  private def hasAmmonite(file: PsiFile): Boolean = {
    val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(file.getProject).getLibraries
    libraries.exists(_.getName.startsWith("ammonite-"))
  }

  private def createBgIndicator(implicit project: Project) =
    ProgressIndicatorProvider.getGlobalProgressIndicator match {
      case null => new BackgroundableProcessIndicator(
        project,
        WorksheetBundle.message("ammonite.config.display.name"),
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

    val ammoniteVersion = loadAmmoniteVersion(scalaVersion).getOrElse(DEFAULT_AMMONITE_VERSION)

    (scalaVersion, ammoniteVersion)
  }

  def loadAmmoniteVersion(scalaVersion: String): Try[String] = {
    val url = s"https://repo1.maven.org/maven2/com/lihaoyi/$AMMONITE_PREFIX$scalaVersion/"
    val lines: Try[Seq[String]] = Versions.loadLinesFrom(url)

    val versions = lines.map(detectAmmoniteVersions)
    val version = versions.map(chooseAmmoniteVersion)
    version.flatMap {
      case Some(v) =>
        Success(v.presentation)
      case _ =>
        // TODO: use something more user-friendly error handling
        throw new RuntimeException(s"Can't detect appropriate ammonite version for scala version `$scalaVersion`")
    }
  }

  private def chooseAmmoniteVersion(versions: Seq[Version]): Option[Version] = {
    // release: 2.1.4
    // dev: 2.1.4-8-5d0c097
    val (dev, release) = versions.partition(_.presentation.contains("-"))
    release.maxOption.orElse(dev.maxOption)
  }

  private def detectAmmoniteVersions(mavenInfoLines: Seq[String]): Seq[Version] = {
    val versions = mavenInfoLines.flatMap(AMMONITE_VERSION_PATTERN.findFirstIn)
    // version inside title contains quotes and slash in the end, e.g. 2.1.4-8-5d0c097/
    val cleaned = versions.map(_.stripPrefix("\"").stripSuffix("\"").stripSuffix("/"))
    cleaned.map(Version.apply)
  }

  // TODO: why "versionS"?
  def loadScalaVersions(forScala: MyScalaVersion): Try[Option[Version]] = {
    val url = "https://repo1.maven.org/maven2/com/lihaoyi/"
    val mavenInfoLines = Versions.loadLinesFrom(url)
    mavenInfoLines.map(detectScalaVersion(_, forScala))
  }

  private def detectScalaVersion(mavenInfoLines: Seq[String], forScala: MyScalaVersion): Option[Version] = {
    val ammoniteLines = {
      val pattern = s"\\Q$AMMONITE_PREFIX\\E$THREE_DIGIT_PATTERN".r
      mavenInfoLines.flatMap(line => pattern.findFirstIn(line))
    }

    val ammoniteVersions = {
      val pattern = THREE_DIGIT_PATTERN.r
      ammoniteLines.flatMap(v => pattern.findFirstIn(v)).map(Version(_))
    }
    val grouped = ammoniteVersions.groupBy(_.major(2).presentation.last)

    forScala match {
      case MajorVersion(m) =>
        grouped.get(m).map(_.last)
      case ExactVersion(m, v) =>
        grouped.get(m).flatMap(_.findLast(v >= _))
    }
  }

  def suggestAddingAmmonite(file: ScFile): Unit = {
    if (hasAmmonite(file)) return
    implicit val project: Project = file.getProject

    //noinspection ScalaExtractStringToBundle
    @Nls
    val ammoniteName = "Ammonite"

    object AddImportsAction extends NotificationAction(WorksheetBundle.message("ammonite.add.all.dependencies.to.project.action.add")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.expire()
        apply(file)
      }
    }
    object IgnoreAction extends NotificationAction(WorksheetBundle.message("ammonite.add.all.dependencies.to.project.action.ignore")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.expire()
        AmmoniteScriptWrappersHolder.getInstance(project).setIgnoreImports()
      }
    }
    NotificationUtil
      .builder(project, WorksheetBundle.message("ammonite.add.all.dependencies.to.project.message"))
      .setTitle(ammoniteName)
      .addAction(AddImportsAction)
      .addAction(IgnoreAction)
      .show()
  }
}
