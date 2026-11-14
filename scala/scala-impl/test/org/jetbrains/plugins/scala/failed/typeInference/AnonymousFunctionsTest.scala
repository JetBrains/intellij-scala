package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

import java.nio.file.Path

class AnonymousFunctionsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: Path = super.folderPath / "bugs5"

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

}
