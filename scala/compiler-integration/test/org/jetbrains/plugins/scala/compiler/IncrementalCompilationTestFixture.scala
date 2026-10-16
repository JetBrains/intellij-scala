package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.{Files, Path}

/**
 * Helpers shared by the incremental compilation suites. Contains no tests of its own.
 */
abstract class IncrementalCompilationTestFixture extends ScalaCompilerTestBase {

  protected def initBuildProject(sourceFiles: SourceFile*): Seq[SourceFile] =
    initBuildProject(sourceFiles.toSeq, allowWarnings = false)

  protected def initBuildProjectAllowWarnings(sourceFiles: SourceFile*): Seq[SourceFile] =
    initBuildProject(sourceFiles.toSeq, allowWarnings = true)

  private def initBuildProject(sourceFiles: Seq[SourceFile], allowWarnings: Boolean): Seq[SourceFile] = {
    compiler.rebuild().assertNoProblems(allowWarnings)

    val actualTargetFileNames = targetFileNames
    val expectedTargetFileNames = sourceFiles.flatMap(_.expectedTargetFileNames).toSet
    assertThat("Failed initial compilation",
      actualTargetFileNames, equalTo(expectedTargetFileNames)
    )
    sourceFiles
  }

  private def targetDir: Path =
    CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath.toNioPath

  protected def targetFileNames: Set[String] =
    targetDir.children().map(_.getFileName.toString).toSet

  protected def classFileNames(className: String)
                              (implicit version: ScalaVersion): Set[String] = {
    val suffixes =
      if (version.isScala3 && !className.endsWith("$")) Set("class", "tasty") // module classes have no TASTy file of their own
      else Set("class")
    suffixes.map(suffix => s"$className.$suffix")
  }

  protected class SourceFile private(name: String)
                                    (implicit version: ScalaVersion) {

    private var classes: Set[String] = Set.empty

    def this(name: String, classes: Set[String], code: String)
            (implicit version: ScalaVersion) = {
      this(name)
      writeCode(classes, code)
    }

    def writeCode(classes: Set[String], code: String): Unit = {
      addFileToProjectSources(sourceFileName, code)
      this.classes = classes
    }

    def removeSourceFile(): Unit = {
      sourceFile.foreach(VfsTestUtil.deleteFile)
      this.classes = Set.empty
    }

    private def sourceFileName: String =
      s"$name.scala"

    private def sourceFile: Option[VirtualFile] =
      Option(getSourceRootDir.findChild(sourceFileName))

    def expectedTargetFileNames: Set[String] =
      classes.flatMap(classFileNames)

    private def targetFiles: Set[Path] = {
      val targetFileNames = expectedTargetFileNames
      targetDir.children().filter(p => targetFileNames.contains(p.getFileName.toString)).toSet
    }

    def targetTimestamps: Map[String, Long] =
      targetFiles.map { targetFile =>
        targetFile.getFileName.toString -> Files.getLastModifiedTime(targetFile).toInstant.toEpochMilli
      }.toMap
  }
}
