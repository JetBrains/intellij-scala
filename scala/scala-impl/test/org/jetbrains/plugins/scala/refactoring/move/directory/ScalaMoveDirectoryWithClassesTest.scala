package org.jetbrains.plugins.scala.refactoring.move.directory

import com.intellij.openapi.vfs.{VirtualFileEvent, VirtualFileListener, VirtualFileManager}
import junit.framework.TestCase.assertFalse
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

import scala.annotation.nowarn

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
class ScalaMoveDirectoryWithClassesTest extends ScalaMoveDirectoryWithClassesTestBase {
  def testRenamePackage(): Unit = doRenamePackageTest("pack1", "pack1.pack2")

  def testRenamePackageUp(): Unit = doRenamePackageTest("pack1.pack2", "pack1")

  def testRenamePackageImportsToNestedClasses(): Unit = doRenamePackageTest("pack1.pack2", "pack0.pack2")

  def testRenamePackageWithPackageObject(): Unit = doRenamePackageTest("pack1.pack2", "pack0.pack1.pack2")

  def testMovePackageWithPackageObject(): Unit = doMovePackageTest("pack1.pack2", "pack3")

  def testMultipleClassesInOneFile(): Unit = {
    var fileWasDeleted = false
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener {
      override def fileDeleted(event: VirtualFileEvent): Unit = if (!event.getFile.isDirectory) fileWasDeleted = true
    }, getTestRootDisposable): @nowarn("cat=deprecation")

    doMovePackageTest()
    assertFalse("Deleted instead of move", fileWasDeleted)
  }

  def testEmptySubDirs(): Unit = doMovePackageTest()
}
