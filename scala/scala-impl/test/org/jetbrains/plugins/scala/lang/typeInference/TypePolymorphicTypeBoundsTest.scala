package org.jetbrains.plugins.scala.lang.typeInference

class TypePolymorphicTypeBoundsTest extends TypeInferenceTestBase {
  def testLub(): Unit = doTest(
    s"""
      |trait Foo
      |class Bar extends Foo
      |class Baz extends Foo
      |
      |def f[F[_], A](ffa: F[F[A]]): F[F[A]] = ffa
      |val a: Either[Bar, Either[Baz, Int]] = ???
      |${START}f(a)$END
      |//Either[Foo, Either[Foo, Int]]
    """.stripMargin
  )
}
