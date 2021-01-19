package org.jetbrains.sbt.project.template

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.module.{ModifiableModuleModel, Module}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.platform.templates.github.ZipUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt

import java.io.File
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.zip.ZipInputStream
import scala.util.Using
class ArchivedSbtProjectBuilder(archiveURL: URL) extends SbtModuleBuilder {

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep =
    new SdkSettingsStep(settingsStep, this, (_: SdkTypeId).is[JavaSdk])

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    new File(getModuleFileDirectory) match {
      case root if root.exists() =>

        Using.resource(new ZipInputStream(archiveURL.openStream)) { stream =>
          ZipUtil.unzip(null, root.toPath, stream, null, null, false)
        }

        processExtractedArchive(root.toPath)

        setModuleFilePath(updateModuleFilePath(getModuleFilePath))
      case _ =>
    }

    super.createModule(moduleModel)
  }

  protected def processExtractedArchive(extractedPath: Path): Unit = ()

  /**
   * Replaces simple strings in file. Doesn't work with regexps
   * @param replacements a map of simple string substitutions
   * @return new file content or a list of error messages
   */
  protected def replaceInFile(relativePath: String, replacements: Map[String, String]): Either[Seq[String], String] = {
    val fullFilePath = Paths.get(getModuleFileDirectory).resolve(relativePath)
    if (Files.exists(fullFilePath)) {
      val newContent = try {
        val content = Files.readString(fullFilePath)
        val (result, errors) = replacements.foldLeft(content -> Seq.empty[String]) ({ case ((text, errors), (from, to)) =>
          if (text.contains(from))
            text.replace(from, to) -> errors
          else
            text -> (errors :+ s"Key '$from' not found in $relativePath")
        })
        if (errors.isEmpty) {
          Files.writeString(fullFilePath, result)
          Right(result)
        } else Left(errors)
      } catch {
        case e: Throwable => Left(Seq(s"Error while processing file $relativePath: ${e.getMessage}"))
      }
      newContent
    } else Left(Seq(s"Target file doesn't exist - $relativePath"))
  }

}