package org.jetbrains.sbt.project

import junit.framework.TestCase
import org.junit.Assert.assertEquals

import java.nio.file.Path

class ExternalSourceRootResolutionTest extends TestCase {

  private def findCommonParent(projectRoot: String, rootDirectories: Seq[String]): Option[Path] =
    ExternalSourceRootResolution.findCommonParentDirectoryForRoots(rootDirectories.map(Path.of(_)), Path.of(projectRoot))

  //Walking up from a root close to the filesystem root must not throw
  def testSharedRootDirectlyUnderShallowProjectRoot(): Unit = {
    val result = findCommonParent(
      projectRoot = "/Users/shared-sources-from-dirs",
      rootDirectories = Seq("/Users/shared-sources-from-dirs/sharedProj"),
    )
    assertEquals(None, result)
  }

  def testProjectAtFilesystemRoot(): Unit = {
    val result = findCommonParent(
      projectRoot = "/",
      rootDirectories = Seq("/shared"),
    )
    assertEquals(None, result)
  }

  def testNonStandardParentInShallowProject(): Unit = {
    val result = findCommonParent(
      projectRoot = "/a",
      rootDirectories = Seq("/a/shared/custom"),
    )
    assertEquals(Some(Path.of("/a/shared")), result)
  }

  def testNonStandardParentAboveStandardDirectories(): Unit = {
    val result = findCommonParent(
      projectRoot = "/home/user/proj",
      rootDirectories = Seq("/home/user/proj/shared/src/main/scala"),
    )
    assertEquals(Some(Path.of("/home/user/proj/shared")), result)
  }

  def testCommonParentOfMultipleRoots(): Unit = {
    val result = findCommonParent(
      projectRoot = "/home/user/proj",
      rootDirectories = Seq(
        "/home/user/proj/shared/src/main/scala",
        "/home/user/proj/shared/src/test/scala",
      ),
    )
    assertEquals(Some(Path.of("/home/user/proj/shared")), result)
  }

  def testNoCommonNonStandardParent(): Unit = {
    val result = findCommonParent(
      projectRoot = "/home/user/proj",
      rootDirectories = Seq(
        "/home/user/proj/shared1/src/main/scala",
        "/home/user/proj/shared2/src/main/scala",
      ),
    )
    assertEquals(None, result)
  }

  def testMostSpecificCommonParentIsSelected(): Unit = {
    val result = findCommonParent(
      projectRoot = "/home/user/proj",
      rootDirectories = Seq(
        "/home/user/proj/outer/inner/custom1",
        "/home/user/proj/outer/inner/custom2",
      ),
    )
    assertEquals(Some(Path.of("/home/user/proj/outer/inner")), result)
  }
}
