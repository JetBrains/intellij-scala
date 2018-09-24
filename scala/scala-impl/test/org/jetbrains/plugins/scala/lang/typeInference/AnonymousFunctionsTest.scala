package org.jetbrains.plugins.scala.lang.typeInference

/**
  * @author Anton Yalyshev
  * @since 07/09/18
  */
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

}
