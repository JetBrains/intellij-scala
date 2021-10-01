package org.jetbrains.sbt.project.template

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.module.{ModifiableModuleModel, Module}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.platform.templates.github.ZipUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.project.template.AbstractArchivedSbtProjectBuilder.replacePatterns

import java.io.File
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.regex.Matcher.quoteReplacement
import java.util.regex.Pattern
import java.util.zip.ZipInputStream
import scala.util.Using

abstract class AbstractArchivedSbtProjectBuilder extends SbtModuleBuilderBase {

  protected def archiveURL: URL

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep =
    new SdkSettingsStep(settingsStep, this, (_: SdkTypeId).is[JavaSdk])

  protected def extractArchive(root: File, url: URL, unwrapSingleTopLevelFolder: Boolean = false): Unit = {
    Using.resource(new ZipInputStream(url.openStream)) { stream =>
      ZipUtil.unzip(null, root.toPath, stream, null, null, unwrapSingleTopLevelFolder)
    }
  }

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    new File(getModuleFileDirectory) match {
      case root if root.exists() =>
        extractArchive(root, archiveURL)
        processExtractedArchive(root.toPath)
        setModuleFilePath(moduleFilePathUpdated(getModuleFilePath))
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
        replacePatterns(content, replacements) match {
          case Right(result) =>
            Files.writeString(fullFilePath, result)
            Right(result)
          case x@Left(_) => x
        }
      } catch {
        case e: Throwable => Left(Seq(s"Error while processing file $relativePath: ${e.getMessage}"))
      }
      newContent
    } else Left(Seq(s"Target file doesn't exist - $relativePath"))
  }

}

object AbstractArchivedSbtProjectBuilder {

  implicit class SbtPatternExt(val str: String) extends AnyVal {
    private def escaped(s: String) =
      s.replaceAll("\\s+", quoteReplacement("\\s+"))

    def keyInit: String =
      s"""(^.+${escaped(str)}\\s*:=\\s*)([^\\s][^,]+)(,.+$$)"""

    def keyInitQuoted: String =
      s"""(^.+${escaped(str)}\\s*:=\\s*")([^",]+)(",.+$$)"""

    def tagBody: String =
      s"""(^.+<$str>)([^<>]+)(</$str>.+$$)"""

    def emptyTagAttr: String = {
      val Array(tag, attr) = str.split('/')
      s"""(^.+<$tag.+$attr=")([^"]+)(".*/>.+$$)"""
    }
  }

  def replacePatterns(content: String, replacements: Map[String, String]): Either[Seq[String], String] = {
    val (result, errors) = replacements.foldLeft(content -> Seq.empty[String]) ({ case ((text, errors), (from, to)) =>
      try {
        val matcher = Pattern.compile(from, Pattern.DOTALL).matcher(text)
        if (matcher.matches())
          matcher.replaceFirst(s"$$1$to$$3") -> errors
        else
          text -> (errors :+ s"Key '$from' not found")
      } catch {
        case c: ControlFlowException => throw c
        case e: Exception =>
          text -> (errors :+ s"Exception during patching '$from': ${e.getMessage}'")
      }
    })
    if (errors.isEmpty) Right(result) else Left(errors)
  }

}