package org.jetbrains.plugins.scala.lang.typeInference

class CurriedTypeParamsApplyCallTest extends TypeInferenceTestBase {
  def testSCL22917_1(): Unit = doTest(
    s"""
       |object A {
       |  def m[A] = new C[A]
       |  class C[A] {
       |    def apply[B](f: A => (B)) = f
       |  }
       |
       |  ${START}m[Int](_.toString -> false)$END
       |}
       |//Int => (String, Boolean)
       |""".stripMargin
  )

  def testSCL22917_2(): Unit = doTest(
    s"""
       |object A {
       |  def m[A] = new C[A]
       |  class C[A] {
       |    def apply[B, C](f: A => (B, C)) = f
       |  }
       |  ${START}m[Int](_.toString -> false)$END
       |}
       |//Int => (String, Boolean)
       |""".stripMargin
  )

  def testSCL23327(): Unit = doTest(
    s"""
       |object Main {
       |    trait IO[A]
       |    trait GenSpawn[F[_], E]
       |    trait Async[F[_]] extends GenSpawn[F, Throwable]
       |    def apply[F[_], E](implicit F: GenSpawn[F, E]): F.type = F
       |    def apply[F[_]](implicit F: GenSpawn[F, _], d: DummyImplicit): F.type = F
       |    implicit val x: Async[IO] = ???
       |
       |    ${START}apply[IO]$END
       |}
       |//Main.Async[Main.IO]
       |""".stripMargin
  )
}
