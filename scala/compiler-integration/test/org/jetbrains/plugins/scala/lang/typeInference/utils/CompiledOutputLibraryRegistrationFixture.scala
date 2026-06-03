package org.jetbrains.plugins.scala.lang.typeInference.utils

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.junit.Assert.assertNotNull

import java.nio.file.{Files, Path, StandardCopyOption}
import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * Test fixture that registers compiled output files as module libraries.
 *
 * Capabilities:
 *  - Creates a temporary classes root for a library
 *  - Copies compiled artifacts into that root while preserving output-relative paths
 *  - Attaches the classes root as a library dependency to the fixture module
 *  - Cleans up attached libraries and generated classes roots in [[tearDown]]
 */
final class CompiledOutputLibraryRegistrationFixture(
  fixture: CodeInsightTestFixture
) {

  private val module = fixture.getModule
  private val attachedLibraries = mutable.ArrayBuffer.empty[Library]
  private val generatedLibraryClassRoots = mutable.ArrayBuffer.empty[Path]

  def registerCompiledOutputAsLibrary(
    compiledOutputPath: Path,
    compiledArtifacts: Seq[Path],
    libraryName: String
  ): Unit = {
    val libraryClassesRootDirectoryName = s"$libraryName-classes-dir"
    val libraryClassesRootPath = createLibraryClassRootWithCompiledClasses(
      compiledOutputPath = compiledOutputPath,
      compiledArtifacts = compiledArtifacts,
      libraryRootDirectoryName = libraryClassesRootDirectoryName
    )
    generatedLibraryClassRoots += libraryClassesRootPath
    val library = attachLibrary(libraryClassesRootPath, libraryName)
    attachedLibraries += library
  }

  /**
   * Removes all libraries added by this fixture and deletes generated classes roots.
   */
  def tearDown(): Unit = {
    attachedLibraries.reverseIterator.foreach(library => PsiTestUtil.removeLibrary(module, library))
    attachedLibraries.clear()

    generatedLibraryClassRoots.reverseIterator.foreach(deleteLibraryRoot)
    generatedLibraryClassRoots.clear()
  }

  private def createLibraryClassRootWithCompiledClasses(
    compiledOutputPath: Path,
    compiledArtifacts: Seq[Path],
    libraryRootDirectoryName: String
  ): Path = {
    val libraryClassesRoot = fixture.getTempDirFixture.findOrCreateDir(libraryRootDirectoryName)
    val libraryClassesRootPath = Path.of(libraryClassesRoot.getPath)
    compiledArtifacts.foreach { sourcePath =>
      val relativePath = compiledOutputPath.relativize(sourcePath)
      val targetPath = libraryClassesRootPath.resolve(relativePath)
      copyCompiledArtifact(sourcePath, targetPath)
    }

    libraryClassesRootPath
  }

  private def copyCompiledArtifact(sourcePath: Path, targetPath: Path): Unit = {
    val targetDirectory = Option(targetPath.getParent)
      .getOrElse(throw new AssertionError(s"Target class path should have a parent: $targetPath"))
    Files.createDirectories(targetDirectory)
    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(targetPath)
  }

  private def attachLibrary(libraryClassesRootPath: Path, libraryName: String): Library = {
    val classesRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(libraryClassesRootPath)
    assertNotNull(s"Could not find library classes root: $libraryClassesRootPath", classesRoot)
    PsiTestUtil.addProjectLibrary(module, libraryName, util.Arrays.asList(classesRoot), util.Collections.emptyList())
  }

  private def deleteLibraryRoot(libraryClassRootPath: Path): Unit = {
    val libraryRootVFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(libraryClassRootPath)
    if (libraryRootVFile != null) {
      VfsTestUtil.deleteFile(libraryRootVFile)
    } else {
      deleteRecursivelyIfExist(libraryClassRootPath)
    }
  }

  private def deleteRecursivelyIfExist(libraryClassRootPath: Path): Unit = {
    if (Files.exists(libraryClassRootPath)) {
      val stream = Files.walk(libraryClassRootPath)
      try {
        val files = stream.iterator().asScala.toSeq.sortBy(_.getNameCount)
        files.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
  }
}
