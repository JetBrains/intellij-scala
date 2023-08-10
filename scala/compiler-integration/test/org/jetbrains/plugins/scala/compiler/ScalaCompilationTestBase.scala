package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.{CompilationTests, ScalaVersion}
import org.junit.experimental.categories.Category

import java.io.File

@Category(Array(classOf[CompilationTests]))
abstract class ScalaCompilationTestBase(override protected val incrementalityType: IncrementalityType,
                               override protected val useCompileServer: Boolean) extends ScalaCompilerTestBase {

  override protected def buildProcessJdk: Sdk = CompileServerLauncher.defaultSdk(getProject)

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

  private def targetDir: File =
    new File(CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath.getCanonicalPath)

  protected def targetFileNames: Set[String] =
    targetDir.listFiles().map(_.getName).toSet

  protected def classFileNames(className: String)
                              (implicit version: ScalaVersion): Set[String] = {
    val suffixes =
      if (version.isScala3) Set("class", "tasty")
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

    private def targetFiles: Set[File] = {
      val targetFileNames = expectedTargetFileNames
      targetDir.listFiles { (_, fileName) =>
        targetFileNames contains fileName
      }.toSet
    }

    def targetTimestamps: Map[String, Long] =
      targetFiles.map { targetFile =>
        targetFile.getName -> targetFile.lastModified()
      }.toMap
  }
}
