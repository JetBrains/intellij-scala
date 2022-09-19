package org.jetbrains.plugins.scala.projectHighlighting.scalaCompilerTestdata

import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightPlatformTestCase
import org.apache.commons.io.FilenameUtils
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.{PsiFileTestUtil, TestUtils}
import org.jetbrains.plugins.scala.{ScalaFileType, ScalacTests}
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

import java.io.File
import scala.io.{Codec, Source}
import scala.util.Using

@Category(Array(classOf[ScalacTests]))
abstract class ScalaCompilerTestdataHighlightingTest
  extends ScalaLightCodeInsightFixtureTestCase {

  override protected val includeReflectLibrary = true
  override protected val includeCompilerAsLibrary = true

  protected def getTestDirName: String

  protected final def getScalaCompilerTestDataRoot: String =
    s"${TestUtils.getTestDataPath}/scalacTests/".replace("\\", "/")

  protected final def getTestDataDir: String =
    s"${getScalaCompilerTestDataRoot}/$getTestDirName/"

  protected def filesToHighlight: Seq[File]

  protected val reporter: HighlightingProgressReporter

  protected def doTest(): Unit = {
    val allFiles = filesToHighlight

    val allFilesGrouped: Seq[(String, Seq[File])] = allFiles
      .filter(f => f.isDirectory || isScalaFile(f) || isFlagsFile(f))
      .groupBy(f => FilenameUtils.removeExtension(f.getPath.replace("\\", "/")))
      .toSeq
      .sortBy(_._1)

    AllProjectHighlightingTest.warnIfUsingRandomizedTests(reporter)

    val testDataPath = getScalaCompilerTestDataRoot

    val groupsTotal = allFilesGrouped.size
    var idx = 0
    for (((basePath, files), groupIndex) <- allFilesGrouped.zipWithIndex) {
      if (groupsTotal > 1) {
        //there can be single group in "Failing tests", see e.g. ScalaCompilerTestdataHighlightingFailingTests_2_12 methods
        val relativeBasePath = basePath.stripPrefix(testDataPath)
        reporter.notifyHighlightingProgress(groupIndex, groupsTotal, relativeBasePath)
      }
      annotateFiles(files, reporter)
      idx += 1
    }
    reporter.reportFinalResults()
  }

  private def addFileToProject(file: File, relativeTo: File): PsiFile = {
    val text: String = content(file)
    val path = relativeTo.toPath.relativize(file.toPath)
    val originalDirName = relativeTo.getName
    val psiFile = PsiFileTestUtil.addFileToProject(path, text, getProject)
    AllProjectHighlightingTest.setOriginalDirName(psiFile, originalDirName)
    psiFile
  }

  private def content(file: File): String =
    Using.resource(Source.fromFile(file)(Codec.UTF8))(_.getLines().mkString("\n"))

  private def removeFile(psiFile: PsiFile): Unit = {
    inWriteAction {
      psiFile.delete()
    }
  }

  private def annotateFiles(files: Seq[File], reporter: HighlightingProgressReporter): Unit = {
    def allFiles(f: File): Seq[File] =
      if (f.isDirectory) f.listFiles.toIndexedSeq.flatMap(allFiles)
      else               Seq(f)

    def parseScalacFlags(f: File): Seq[String] =
      Using.resource(Source.fromFile(f, "UTF-8"))(_.getLines().map(_.trim).filter(_.nonEmpty).toList)

    val root = files match {
      case Seq(file) if file.isDirectory => file
      case Seq(file, _*)                 => file.getParentFile
    }

    val (flagFiles, sourceFiles) = files.flatMap(allFiles).partition(isFlagsFile)

    val sourceRootFiles = LightPlatformTestCase.getSourceRoot.getChildren
    assertTrue(
      s"Expecting no files in source root before annotating files, but got:\n${sourceRootFiles.mkString("\n")}",
      sourceRootFiles.isEmpty
    )

    val addedFiles = sourceFiles.map(addFileToProject(_, relativeTo = root))

    val compilerProfile = getModule.scalaCompilerSettingsProfile
    try {
      val newSettings = compilerProfile.getSettings.copy(
        additionalCompilerOptions = flagFiles.flatMap(parseScalacFlags).toIndexedSeq
      )
      compilerProfile.setSettings(newSettings)
      ScalaCompilerConfiguration.incModificationCount()

      addedFiles.foreach(AllProjectHighlightingTest.annotateScalaFile(_, reporter))
    } finally {
      addedFiles.foreach(removeFile)
      inWriteAction {
        //some folders may remain
        LightPlatformTestCase.getSourceRoot.getChildren.foreach(_.delete(this))
      }

      val newSettings = compilerProfile.getSettings.copy(
        additionalCompilerOptions = Seq.empty
      )
      compilerProfile.setSettings(newSettings)
      ScalaCompilerConfiguration.incModificationCount()
    }
  }

  private def isScalaFile(f: File) = f.getName.endsWith(ScalaFileType.INSTANCE.getDefaultExtension)

  private def isFlagsFile(f: File) = f.getName.endsWith("flags")
}