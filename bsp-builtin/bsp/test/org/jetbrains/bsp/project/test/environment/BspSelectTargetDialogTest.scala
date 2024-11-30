package org.jetbrains.bsp.project.test.environment

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import org.junit.Assert.assertEquals
import org.junit.Test

class BspSelectTargetDialogTest {

  @Test
  def visibleNamesForValidUris(): Unit = {
    val uris = List(
      new BuildTargetIdentifier("file:///home/user/project?id=abc"),
      new BuildTargetIdentifier("file:///home/user/project?id=def&foo=bar")
    )
    assertEquals(List("abc", "def"), BspSelectTargetDialog.visibleNames(uris))
  }

  @Test
  def visibleNameForInvalidUris(): Unit = {
    val uris = List(
      new BuildTargetIdentifier("file:///home/user/project?id=abc"),
      new BuildTargetIdentifier("file:///home/user/project?foo=bar") // Missing `id` field
    )
    assertEquals(List("abc", "file:///home/user/project?foo=bar"), BspSelectTargetDialog.visibleNames(uris))
  }
}
