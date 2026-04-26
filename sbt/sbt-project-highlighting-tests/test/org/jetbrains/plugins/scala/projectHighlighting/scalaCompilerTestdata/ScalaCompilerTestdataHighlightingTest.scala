package org.jetbrains.plugins.scala.projectHighlighting.scalaCompilerTestdata

import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightPlatformTestCase
import org.apache.commons.io.FilenameUtils
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.PsiFileTestUtil
import org.jetbrains.plugins.scala.{ScalaFileType, ScalacTests}
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import scala.jdk.StreamConverters.StreamHasToScala
import scala.reflect.NameTransformer
import scala.util.Using

@Category(Array(classOf[ScalacTests]))
abstract class ScalaCompilerTestdataHighlightingTest
  extends ScalaLightCodeInsightFixtureTestCase {

  override protected val includeReflectLibrary = true
  override protected val includeCompilerAsLibrary = true

  protected def reporter: HighlightingProgressReporter

  protected def getTestDirName: String

  protected final def getScalaCompilerTestDataRoot: String =
    Path.of(TestUtils.getTestDataPath, "scalacTests").toString.replace("\\", "/")

  protected final def getTestDataDir: String =
    s"${getScalaCompilerTestDataRoot}/$getTestDirName/"

  protected def getSingleTestFileNameForThisTest = getTestName(/*lowercaseFirstLetter*/ false).stripPrefix("_")

  protected def doTestForFileOrDirectory(): Unit = {
    doTest(Seq(getSingleTestFileOrDirectoryForThisTest))
  }

  private def getSingleTestFileOrDirectoryForThisTest: Path = {
    val decoded = NameTransformer.decode(getSingleTestFileNameForThisTest)
    val dirPath = getTestDataDir + decoded
    val dir = Path.of(dirPath)
    val file = Path.of(s"$dirPath.scala")

    if (dir.exists)
      dir
    else if (file.exists)
      file
    else
      throw new RuntimeException("No file exists")
  }

  protected def doTest(allFilesAndDirsToHighlight: Seq[Path]): Unit = {
    val allFilesGrouped: Seq[(String, Seq[Path])] = allFilesAndDirsToHighlight
      .filter(f => f.isDirectory || isScalaFile(f) || isFlagsFile(f))
      .groupBy(f => FilenameUtils.removeExtension(f.toCanonicalPath.toString.replace("\\", "/")))
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

  private def addFileToProject(file: Path, relativeTo: Path): PsiFile = {
    val text: String = content(file)
    val path = relativeTo.relativize(file)
    val originalDirName = relativeTo.getFileName.toString
    val psiFile = PsiFileTestUtil.addFileToProject(path, text, getProject)
    AllProjectHighlightingTest.setOriginalDirName(psiFile, originalDirName)
    psiFile
  }

  private def content(file: Path): String = Files.readString(file)

  private def removeFile(psiFile: PsiFile): Unit = {
    inWriteAction {
      psiFile.delete()
    }
  }

  private def annotateFiles(files: Seq[Path], reporter: HighlightingProgressReporter): Unit = {
    def allFiles(f: Path): Seq[Path] =
      if (f.isDirectory) f.children().flatMap(allFiles)
      else               Seq(f)

    def parseScalacFlags(f: Path): Seq[String] =
      Using.resource(Files.lines(f))(_.toScala(Seq).map(_.trim).filter(_.nonEmpty))

    val root = files match {
      case Seq(file) if file.isDirectory => file
      case Seq(file, _*)                 => file.getParent
    }

    val (flagFiles, sourceFiles) = files.flatMap(allFiles).partition(isFlagsFile)

    val sourceRootFiles = LightPlatformTestCase.getSourceRoot.getChildren
    assertTrue(
      s"Expecting no files in source root before annotating files, but got:\n${sourceRootFiles.mkString("\n")}",
      sourceRootFiles.isEmpty
    )

    val addedFiles = sourceFiles.map(addFileToProject(_, relativeTo = root))

    val compilerProfile = ScalaCompilerSettingsProfile.forModule(getModule)
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

  private def isScalaFile(f: Path) = f.getFileName.toString.endsWith(ScalaFileType.INSTANCE.getDefaultExtension)

  private def isFlagsFile(f: Path) = f.getFileName.toString.endsWith("flags")
}
