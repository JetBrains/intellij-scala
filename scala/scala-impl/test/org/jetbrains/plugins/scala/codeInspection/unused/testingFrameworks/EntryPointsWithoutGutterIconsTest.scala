package org.jetbrains.plugins.scala.codeInspection.unused.testingFrameworks

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.LibrariesOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedDeclarationInspectionTestBase
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction

/*
 * Most testing framework methods are marked as used by the fact that they are registered as entrypoints,
 * and all entrypoints are marked as used.
 *
 * For the cases where entrypoints are currently not registered as such, we rely on the fact that
 * such methods are defined in files under a test source directory. More precisely, we rely on
 * TestSourcesFilter.isTestSources.
 *
 * Unit tests for testing frameworks that do not depend on isTestSources to determine usedness, do not
 * belong here and should be moved elsewhere as soon as we've implemented entrypoint registration for them.
 */

class EntryPointsWithoutGutterIconsTest extends ScalaUnusedDeclarationInspectionTestBase with LibrariesOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.scalatest" %% "scalatest" % "3.1.1").transitive())
  )

  override def onFileCreated(file: PsiFile): Unit =
    inWriteCommandAction {
      val testDir = file.getContainingDirectory.createSubdirectory("test")
      val testFile = testDir.copyFileFrom("bbb.scala", getFile).getVirtualFile
      getFixture.openFileInEditor(testFile)
    }(file.getProject)

  def test_ref_spec_methods(): Unit = {
    checkTextHasNoErrors(
      s"""
         |import org.scalatest.refspec.RefSpec
         |class Foo extends RefSpec {
         |  //noinspection ScalaUnusedDeclaration
         |  def `should pass`(): Unit = assert(true)
         |}
         |""".stripMargin
    )
  }

}