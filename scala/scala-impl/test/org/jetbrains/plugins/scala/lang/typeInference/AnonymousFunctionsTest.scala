package org.jetbrains.plugins.scala.lang.typeInference

class AnonymousFunctionsTest extends TypeInferenceTestBase {

  def testSCL9701(): Unit = doTest {
    """
      |def f(arg: (String*) => Unit) = {}
      |def ff(arg: Seq[String]) = {}
      |
      |f(s => ff(/*start*/s/*end*/))
      |//Seq[String]
    """.stripMargin.trim
  }

  // A function literal's result type may not mention its own parameters (scalac's `typedFunction`
  // packs them): `get(k)` is a `k.V`, the literal a `Key => Key#V`.
  def testParameterDoesNotEscapeIntoResultType(): Unit = doTest {
    """
      |trait Key { type V }
      |def get(k: Key): k.V = ???
      |/*start*/(k: Key) => get(k)/*end*/
      |//Key => Key#V
    """.stripMargin.trim
  }

  def testParameterDoesNotEscapeIntoInferredElementType(): Unit = doTest {
    """
      |trait Key { type V }
      |def get(k: Key): k.V = ???
      |val ks: List[Key] = Nil
      |/*start*/ks.map(k => get(k))/*end*/
      |//List[Key#V]
    """.stripMargin.trim
  }

  // inside the literal the parameter's path is kept
  def testParameterPathInsideLiteral(): Unit = doTest {
    """
      |trait Key { type V }
      |def get(k: Key): k.V = ???
      |(k: Key) => /*start*/get(k)/*end*/
      |//k.V
    """.stripMargin.trim
  }

}
