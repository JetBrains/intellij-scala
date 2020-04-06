package org.jetbrains.bsp.project.test.environment

import java.net.URI

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class BspSelectTargetDialogTest extends AssertionsForJUnit{

  @Test
  def visibleNamesForValidUris: Unit = {
    val uris = List (
      new URI("file:///home/user/project?id=abc"),
      new URI("file:///home/user/project?id=def&foo=bar")
    )
    assert{
      BspSelectTargetDialog.visibleNames(uris) == List("abc", "def")
    }
  }

  @Test
  def visibleNameForInvalidUris: Unit = {
    val uris = List (
      new URI("file:///home/user/project?id=abc"),
      new URI("file:///home/user/project?foo=bar") // Missing `id` field
    )
    assert{
      BspSelectTargetDialog.visibleNames(uris) == List("abc", "file:///home/user/project?foo=bar")
    }
  }

}
