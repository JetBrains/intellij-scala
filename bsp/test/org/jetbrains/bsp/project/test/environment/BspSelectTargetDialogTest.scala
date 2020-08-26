package org.jetbrains.bsp.project.test.environment

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class BspSelectTargetDialogTest extends AssertionsForJUnit {

  @Test
  def visibleNamesForValidUris: Unit = {
    val uris = List(
      new BuildTargetIdentifier("file:///home/user/project?id=abc"),
      new BuildTargetIdentifier("file:///home/user/project?id=def&foo=bar")
    )
    assert{
      BspSelectTargetDialog.visibleNames(uris) == List("abc", "def")
    }
  }

  @Test
  def visibleNameForInvalidUris: Unit = {
    val uris = List(
      new BuildTargetIdentifier("file:///home/user/project?id=abc"),
      new BuildTargetIdentifier("file:///home/user/project?foo=bar") // Missing `id` field
    )
    assert{
      BspSelectTargetDialog.visibleNames(uris) == List("abc", "file:///home/user/project?foo=bar")
    }
  }

}
