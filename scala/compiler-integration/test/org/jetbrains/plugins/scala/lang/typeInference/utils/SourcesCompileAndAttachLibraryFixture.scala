package org.jetbrains.plugins.scala.lang.typeInference.utils

import com.intellij.openapi.roots.{CompilerModuleExtension, ModuleRootManager}
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.{CompilerTester, VfsTestUtil}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertNoErrors, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.lang.typeInference.SourceFile
import org.junit.Assert.{assertNotNull, assertTrue}

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IteratorHasAsScala}

/**
 * Test fixture for compiled-class library scenarios in the type-inference highlighting tests
 */
final class SourcesCompileAndAttachLibraryFixture(
  fixture: CodeInsightTestFixture,
  compiler: CompilerTester,
  scalaVersion: ScalaVersion
) {

  import SourcesCompileAndAttachLibraryFixture.CompiledOutputFiles

  private val module = fixture.getModule
  private val compiledOutputLibraryFixture = new CompiledOutputLibraryRegistrationFixture(fixture)

  /**
   * Compiles source code and immediately registers the compiled class artifacts as a module library.
   *
   * It reuses the test case module for compilation, thus it reuses the same scala version and compiler options.
   * The sources are temporarily added, compiled, and immediately removed after compilation to make sure the test state
   * does not have any unexpected source files
   *
   * The method must be invoked only before any sources are added to the test module by any means
   */
  def compileSourcesAndRegisterAsLibrary(
    sources: Seq[SourceFile],
    allowWarnings: Boolean,
    libraryName: String = "my-library"
  ): Unit = {
    val compiledOutputFiles = compileSourcesAndCollectOutputFiles(sources, allowWarnings)
    compiledOutputLibraryFixture.registerCompiledOutputAsLibrary(
      compiledOutputPath = compiledOutputFiles.outputPath,
      compiledArtifacts = compiledOutputFiles.allFiles,
      libraryName = libraryName
    )
  }

  /**
   * Removes libraries and generated classes roots created via [[CompiledOutputLibraryRegistrationFixture]].
   */
  def tearDown(): Unit = {
    compiledOutputLibraryFixture.tearDown()

    // We intentionally do not delete compiler output files from the original output directory here.
    // In this test hierarchy, output roots are managed by CompilerTester, and they are removed in CompilerTester.tearDown().
  }

  private def compileSourcesAndCollectOutputFiles(
    sources: Seq[SourceFile],
    allowWarnings: Boolean
  ): CompiledOutputFiles = {
    assertModuleHasNoSourceFiles(stage = "before compilation")

    val addedSourceFiles = sources.map { source =>
      fixture.addFileToProject(source.sourceFileName, source.sourceFileContent)
    }

    try {
      val messages = compiler.make().asScala.toSeq
      if (allowWarnings) {
        assertNoErrors(messages)
      } else {
        assertNoErrorsOrWarnings(messages)
      }

      val compiledOutputFiles = collectCompiledOutputFiles()
      assertTrue("No class files were found in compiler output", compiledOutputFiles.classFiles.nonEmpty)
      if (scalaVersion.isScala3) {
        assertTrue("No tasty files were found in compiler output for Scala 3", compiledOutputFiles.tastyFiles.nonEmpty)
      }
      compiledOutputFiles
    } finally {
      addedSourceFiles.foreach(file => VfsTestUtil.deleteFile(file.getVirtualFile))
      assertModuleHasNoSourceFiles(stage = "after cleanup")
    }
  }

  private def assertModuleHasNoSourceFiles(stage: String): Unit = {
    val existingSourceFiles = moduleSourceFiles
    assertTrue(
      s"Expected module `${module.getName}` to have no source files $stage, but found:\n${existingSourceFiles.map(_.getPath).mkString("\n")}",
      existingSourceFiles.isEmpty
    )
  }

  private def moduleSourceFiles: Seq[VirtualFile] =
    ModuleRootManager.getInstance(module).getSourceRoots.toSeq.flatMap(collectFilesRecursively)

  private def collectFilesRecursively(root: VirtualFile): Seq[VirtualFile] = {
    val files = mutable.ArrayBuffer.empty[VirtualFile]
    VfsUtilCore.iterateChildrenRecursively(root, null, (file: VirtualFile) => {
      if (!file.isDirectory) {
        files += file
      }
      true
    })
    files.toSeq
  }

  private def collectCompiledOutputFiles(): CompiledOutputFiles = {
    val outputPath = compilerOutputPath
    val outputFilesStream = Files.walk(outputPath)
    try {
      val outputFiles = outputFilesStream.iterator().asScala
        .filter(Files.isRegularFile(_))
        .toSeq

      val classFiles = outputFiles.filter(_.getFileName.toString.endsWith(".class"))
      val tastyFiles = outputFiles.filter(_.getFileName.toString.endsWith(".tasty"))
      CompiledOutputFiles(outputPath, classFiles, tastyFiles)
    } finally {
      outputFilesStream.close()
    }
  }

  private def compilerOutputPath: Path = {
    val compilerOutput = CompilerModuleExtension.getInstance(module).getCompilerOutputPath
    assertNotNull("Could not determine compiler output path", compilerOutput)
    Path.of(compilerOutput.getPath)
  }
}

object SourcesCompileAndAttachLibraryFixture {

  private final case class CompiledOutputFiles(
    outputPath: Path,
    classFiles: Seq[Path],
    tastyFiles: Seq[Path]
  ) {
    def allFiles: Seq[Path] = classFiles ++ tastyFiles
  }
}
