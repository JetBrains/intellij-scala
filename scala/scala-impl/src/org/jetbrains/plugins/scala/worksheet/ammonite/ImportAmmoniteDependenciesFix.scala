package org.jetbrains.plugins.scala.worksheet.ammonite

import java.io.File

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress._
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.{ModuleRootManager, OrderRootType}
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.project.template.{Artifact, Downloader}
import org.jetbrains.plugins.scala.project.{Platform, Version, Versions}
import org.jetbrains.plugins.scala.util.{NotificationUtil, ScalaUtil}
import org.jetbrains.plugins.scala.worksheet.ammonite.ImportAmmoniteDependenciesFix.{ExactVersion, MajorVersion}

import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}

/**
  * User: Dmitry.Naydanov
  * Date: 17.01.18.
  */
class ImportAmmoniteDependenciesFix(project: Project) {
  def invoke(file: PsiFile): Unit = {
    val platform = Platform.Scala
    
    val ((scalaVersion, ammoniteVersion), needScalaLib) = ScalaUtil.getScalaVersion(file) match { 
      case Some(v) => 
        (ImportAmmoniteDependenciesFix.detectAmmoniteVersion(ExactVersion(v.charAt(3), Version(v))), false)
      case _ => 
        (ImportAmmoniteDependenciesFix.detectAmmoniteVersion(MajorVersion('2')), true)
    } 
    
    val task = new Task.Backgroundable(project, "Adding dependencies", false) {
      override def run(indicator: ProgressIndicator): Unit = {
        val buffer = ListBuffer[File]()

        Downloader.createTempSbtProject(platform, scalaVersion,
          (text: String) => {
            val e = new AmmoniteUtil.RegexExtractor
            import e._
            
            text.trim match {
              case mre"[info] * Attributed($filePath)" => 
                val f = new File(filePath)
                if (f.exists()) buffer += f
              case mre"[success]$_" =>
                ScalaUtil.getModuleForFile(file.getVirtualFile, project).foreach {
                  module =>
                    extensions.invokeLater {
                      extensions.inWriteAction {
                        createAndAdd(buffer, module, !needScalaLib)
                      }
                    }
                }
              case mre"[error]$content" => 
                ImportAmmoniteDependenciesFix.LOG.warn(s"Ammonite, error while importing dependencies: $content")
              case _ =>
            }
          },
          (_: Platform, version: String) => { 
            Seq(
              s"""set scalaVersion := "$version"""",
              "set libraryDependencies += \"com.lihaoyi\" " + "%" + " \"ammonite\" " + "%" + " \"" + ammoniteVersion + "\" " + "%" + " \"test\" cross CrossVersion.full",
              "updateClassifiers",
              "show test:dependencyClasspath"
            )
          }
        )
      }
    }
    
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
      task, ImportAmmoniteDependenciesFix.createBgIndicator(project, "Ammonite: adding dependencies...")
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
  
  private val AMMONITE_PREFIX = "ammonie_"
  
  private trait MyScalaVersion
  private case class MajorVersion(m: Char) extends MyScalaVersion // m is last num in major version, e.g. 1 for 2.11
  private case class ExactVersion(m: Char, v: Version) extends MyScalaVersion
  
  private def hasAmmonite(file: PsiFile): Boolean =
    ProjectLibraryTable.getInstance(file.getProject).getLibraries.exists(_.getName.startsWith("ammonite-"))

  private def createBgIndicator(project: Project, name: String): ProgressIndicator = 
    Option(ProgressIndicatorProvider.getGlobalProgressIndicator).getOrElse(
      new BackgroundableProcessIndicator(
        project, name, PerformInBackgroundOption.ALWAYS_BACKGROUND, null, null, false
      )
    )

  private def detectAmmoniteVersion(forScala: MyScalaVersion): (String, String) = {
    val scalaVersion = loadVersions(forScala) match {
      case Success(Some(v)) => v.presentation
      case _ => DEFAULT_SCALA_VERSION
    }
    
    val ammoniteVersion = Versions.loadLinesFrom(s"http://repo1.maven.org/maven2/com/lihaoyi/$AMMONITE_PREFIX$scalaVersion/").map {
      lines => 
        lines.flatMap {
          line => Versions.THREE_DIGIT_PATTERN.findFirstIn(line)
        }.map(Version(_))
    }.filter(_.nonEmpty).map(_.max.presentation).getOrElse(DEFAULT_AMMONITE_VERSION)
    
    (scalaVersion, ammoniteVersion)
  }
  
  private def loadVersions(forScala: MyScalaVersion): Try[Option[Version]] = {
    Versions.loadLinesFrom("http://repo1.maven.org/maven2/com/lihaoyi/").map {
      lines => lines.dropWhile(!_.startsWith(AMMONITE_PREFIX)).takeWhile(_.startsWith(AMMONITE_PREFIX))
    }.filter(_.nonEmpty).map {
      ammoniteStrings => 
        ammoniteStrings.map(v => v stripPrefix AMMONITE_PREFIX stripSuffix "/").map(Version(_))
    }.map {
      ammoniteVersions => 
        ammoniteVersions.groupBy(_.presentation.last)
    }.map {
      grouped => 
        forScala match {
          case MajorVersion(m) => 
            grouped.get(m).map(_.last)
          case ExactVersion(m, v) => 
            grouped.get(m).flatMap {
              f => 
                f.reverse.find(v >= _)
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
          | <p>You can add all Ammonite standard dependencies to the project</p> 
          | <a href="ftp://ok">Run</a> <a href="ftp://notok">Ignore</a>
          | </body>
          | </html>
        """.stripMargin,
      handler = {
        case "ok" =>
          new ImportAmmoniteDependenciesFix(project).invoke(file)
        case _ =>
      }
    )
  }
}