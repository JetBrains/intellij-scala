package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class TptToDesignatorConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL17725(): Unit = checkTextHasNoErrors(
    """
      |trait FF[F[_]]
      |type Id[A] = A
      |val a1: FF[({ type L[A] = Id[Option[A]]})#L] = ???
      |val a2: FF[Option] = a1
      |
      |val a22: FF[Option] = ???
      |val a11: FF[({ type L[A] = Id[Option[A]]})#L] = a22
      |""".stripMargin
  )

  def testSCL17725Alias(): Unit = checkTextHasNoErrors(
    """
      |trait FF[F[_]]
      |type Id[A] = A
      |type MyOption[A] = A
      |
      |val a1: FF[({ type L[A] = Id[MyOption[A]]})#L] = ???
      |val a2: FF[MyOption] = a1
      |
      |val a22: FF[MyOption] = ???
      |val a11: FF[({ type L[A] = Id[MyOption[A]]})#L] = a22
      |""".stripMargin
  )
}
