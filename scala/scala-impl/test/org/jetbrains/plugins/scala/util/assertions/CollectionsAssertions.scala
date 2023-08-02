package org.jetbrains.plugins.scala.util.assertions

import org.junit.ComparisonFailure

trait CollectionsAssertions {

  def assertCollectionEquals[T, C[_] <: Iterable[_]](expected: C[T], actual: C[T]): Unit =
    assertCollectionEquals("", expected, actual)

  def assertCollectionEquals[T, C[_] <: Iterable[_]](message: String, expected: C[T], actual: C[T]): Unit =
    if (expected != actual)
      throw new ComparisonFailure(message, expected.mkString("\n"), actual.mkString("\n"))
}

object CollectionsAssertions extends CollectionsAssertions
