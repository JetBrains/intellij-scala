package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Alefas
  * @since 25/03/16
  */
class AnonymousFunctionsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL8621(): Unit = doTest {
    """
      |trait A[T] {
      |  def foo(x : T => T)
      |}
      |
      |trait B {
      |  def f(p : A[_]) {
      |    p.foo(/*start*/x => x/*end*/)
      |  }
      |}
      |//(_$1) => _$1
    """.stripMargin.trim
  }

  def testSCL11637(): Unit = doTest {
    """
      |trait IA {
      |  type T <: IAT
      |  trait IAT {
      |    //...
      |  }
      |}
      |
      |object A {
      |  def Make(): IA = new IA {
      |    case class T() extends IAT {
      |      // ...
      |    }
      |  }
      |}
      |
      |trait IB[V] {
      |  type T <: IBT[V]
      |  trait IBT[K] {
      |    def filter(f: K => Boolean): T
      |  }
      |  val empty: T
      |}
      |
      |object B {
      |  def Make(D: IA): IB[D.T] = new IB[D.T] {
      |    case class T() extends IBT[D.T] {
      |      def filter(f: D.T => Boolean): T = { this }
      |    }
      |    val empty: T = T()
      |  }
      |}
      |
      |package object global {
      |  val MA = A.Make()
      |  val MB = B.Make(MA)
      |
      |  def test(k: MA.T): Boolean = true
      |  MB.empty.filter(k => test(/*start*/k/*end*/) )   // here the type of 'k' is inferenced to IA#T, and it does not interoperable with the type MA.T in the editor.
      |}
      |//global.MA.T
    """.stripMargin.trim
  }

}
