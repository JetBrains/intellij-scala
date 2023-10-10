package org.jetbrains.plugins.scala.util.assertions

import org.junit.ComparisonFailure

trait CollectionsAssertions {

  def assertCollectionEquals[T, C[_] <: Iterable[_]](expected: C[T], actual: C[T]): Unit =
    assertCollectionEquals("", expected, actual)

  def assertCollectionEquals[T, C[_] <: Iterable[_]](message: String, expected: C[T], actual: C[T]): Unit =
    if (expected != actual)
      throw new ComparisonFailure(
        message,
        //NOTE: technically it's not very good to use `orNull`
        //it's impossible to distinguish the case when we have collection with single element `null` and actual `null`
        //But it only affects the diff view nad this should be fine in 99.99% cases
        Option(expected).map(_.mkString("\n")).orNull,
        Option(actual).map(_.mkString("\n")).orNull,
      )
}

object CollectionsAssertions extends CollectionsAssertions
