package org.jetbrains.plugins.scala.worksheet.ammonite

import java.io.File

import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress._
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.{ModuleRootManager, OrderRootType}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.{inWriteAction, invokeLater}
import org.jetbrains.plugins.scala.project.template.{Artifact, Downloader}
import org.jetbrains.plugins.scala.project.{Version, Versions}
import org.jetbrains.plugins.scala.util.{NotificationUtil, ScalaUtil}
import org.jetbrains.plugins.scala.worksheet.ammonite.ImportAmmoniteDependenciesFix.{ExactVersion, MajorVersion}

import scala.collection.mutable
import scala.util.{Success, Try}

/**
  * User: Dmitry.Naydanov
  * Date: 17.01.18.
  */
class ImportAmmoniteDependenciesFix(project: Project) {
  def invoke(file: PsiFile): Unit = {
    val manager = ProgressManager.getInstance

    val task = new Task.Backgroundable(project, "Adding dependencies", false) {
      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setText("Ammonite: loading list of versions...")
        
        val ((scalaVersion, ammoniteVersion), needScalaLib) = ScalaUtil.getScalaVersion(file) match {
          case Some(v) =>
            (ImportAmmoniteDependenciesFix.detectAmmoniteVersion(ExactVersion(v.charAt(3), Version(v))), false)
          case _ =>
            (ImportAmmoniteDependenciesFix.detectAmmoniteVersion(MajorVersion('2')), true)
        }

        indicator.setText("Ammonite: extracting info from SBT...")

        import Downloader._

        val processAdapter = new DownloadProcessAdapter(manager) {

          private val buffer = mutable.ListBuffer.empty[File]

          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
            val e = new AmmoniteUtil.RegexExtractor
            import e._

            event.getText.trim match {
              case mre"[info] * Attributed($filePath)" =>
                val f = new File(filePath)
                if (f.exists()) buffer += f
              case mre"[success]$_" =>
                indicator.setText("Ammonite: adding dependencies...")

                ScalaUtil.getModuleForFile(file.getVirtualFile, project).foreach { module =>
                  invokeLater {
                    inWriteAction {
                      createAndAdd(buffer, module, !needScalaLib)
                    }
                  }
                }
              case mre"[error]$content" =>
                ImportAmmoniteDependenciesFix.LOG.warn(s"Ammonite, error while importing dependencies: $content")
              case _ =>
            }
          }
        }
        createTempSbtProject(
          scalaVersion,
          processAdapter,
          setScalaSBTCommand(scalaVersion),
          "set libraryDependencies += \"com.lihaoyi\" " + "%" + " \"ammonite\" " + "%" + " \"" + ammoniteVersion + "\" " + "%" + " \"test\" cross CrossVersion.full",
          UpdateClassifiersSBTCommand,
          "show test:dependencyClasspath"
        )
      }
    }

    manager.runProcessWithProgressAsynchronously(
      task, ImportAmmoniteDependenciesFix.createBgIndicator(project, "Ammonite")
    )
  }

  private def createAndAdd(files: Seq[File], module: Module, needFiltering: Boolean = true) {
    def selectName(file: File) = file.getName
    
    def filterScala(file: File) = file.getName.startsWith("scala-") && 
      Artifact.ScalaArtifacts.exists(p => p.versionOf(file).isDefined)
    
    val tableModel = ProjectLibraryTable.getInstance(project).getModifiableModel
    val moduleModel = ModuleRootManager.getInstance(module).getModifiableModel
    
    (if (needFiltering) files filterNot filterScala else files) foreach {
      file =>
        Option(JarFileSystem.getInstance().findLocalVirtualFileByPath(file.getCanonicalPath)) foreach {
          jarRoot =>
            val lib = tableModel.createLibrary(selectName(file))
            val model = lib.getModifiableModel
            model.addRoot(jarRoot, OrderRootType.CLASSES)
            model.commit()
            moduleModel.addLibraryEntry(lib)
        }
    }

    tableModel.commit()
    moduleModel.commit()
  }
}

object ImportAmmoniteDependenciesFix {
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.worksheet.ammonite.ImportAmmoniteDependenciesFix")
  
  private val DEFAULT_SCALA_VERSION = "2.12.4"
  private val DEFAULT_AMMONITE_VERSION = "1.0.3"
  
  private val AMMONITE_PREFIX = "ammonite_"
  private val THREE_DIGIT_PATTERN = "(\\d+\\.\\d+\\.\\d+)"
  
  trait MyScalaVersion
  case class MajorVersion(m: Char) extends MyScalaVersion // m is last num in major version, e.g. 1 for 2.11
  case class ExactVersion(m: Char, v: Version) extends MyScalaVersion
  
  private def hasAmmonite(file: PsiFile): Boolean =
    ProjectLibraryTable.getInstance(file.getProject).getLibraries.exists(_.getName.startsWith("ammonite-"))

  private def createBgIndicator(project: Project, name: String): ProgressIndicator = 
    Option(ProgressIndicatorProvider.getGlobalProgressIndicator).getOrElse(
      new BackgroundableProcessIndicator(
        project, name, PerformInBackgroundOption.ALWAYS_BACKGROUND, null, null, false
      )
    )

  def detectAmmoniteVersion(forScala: MyScalaVersion): (String, String) = {
    val scalaVersion = loadScalaVersions(forScala) match {
      case Success(Some(v)) => v.presentation
      case _ => DEFAULT_SCALA_VERSION
    }
    
    val ammoniteVersion = loadAmmoniteVersion(forScala, scalaVersion).getOrElse(DEFAULT_AMMONITE_VERSION)
    
    (scalaVersion, ammoniteVersion)
  }
  
  def loadAmmoniteVersion(forScala: MyScalaVersion, scalaVersion: String): Try[String] = {
    Versions.loadLinesFrom(s"http://repo1.maven.org/maven2/com/lihaoyi/$AMMONITE_PREFIX$scalaVersion/").map {
      lines =>
        val pattern = ("\\Q\"\\E" + THREE_DIGIT_PATTERN).r

        lines.flatMap {
          line => pattern findFirstIn line
        }.map(str => Version(str stripPrefix "\""))
    }.filter(_.nonEmpty).map(_.max.presentation)
  }
  
  def loadScalaVersions(forScala: MyScalaVersion): Try[Option[Version]] = {
    Versions.loadLinesFrom("http://repo1.maven.org/maven2/com/lihaoyi/").map {
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

  def suggestAddingAmmonite(file: PsiFile) {
    if (hasAmmonite(file)) return
    val project = file.getProject

    NotificationUtil.showMessage(
      project = project,
      title = "Ammonite", 
      message =
        """
          | <html>
          | <body>
          | <p><a href="ftp://run">Add</a> all Ammonite standard dependencies to the project? <a href="ftp://disable">Ignore</a></p>
          | </body>
          | </html>
        """.stripMargin,
      handler = {
        case "run" =>
          new ImportAmmoniteDependenciesFix(project).invoke(file)
        case "disable" => 
          AmmoniteScriptWrappersHolder.getInstance(project).setIgnoreImports()
        case _ =>
      }
    )
  }
}