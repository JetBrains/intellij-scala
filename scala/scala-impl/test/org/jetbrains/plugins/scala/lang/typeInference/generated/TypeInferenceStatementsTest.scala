package org.jetbrains.plugins.scala
package lang
package typeInference
package generated

abstract class TypeInferenceStatementsTestBase extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "statements/"
}

class TypeInferenceStatementsTest extends TypeInferenceStatementsTestBase {
  def testAssignStatement(): Unit = doTest()

  def testAssignWithFunction(): Unit = doTest()

  def testForStatementWithGuard(): Unit = doTest()

  def testForStatementWithGuard2(): Unit = doTest()

  def testIfStatement(): Unit = doTest()

  def testImportedParameterizedType(): Unit = doTest()

  def testIncompleteForStatement(): Unit = doTest()

  def testInfix(): Unit = doTest()

  def testMatchStatement(): Unit = doTest()

  def testOptionLub(): Unit = doTest()

  def testOverridingCheck(): Unit = doTest()

  def testThisStmt(): Unit = doTest()

  def testTryStatement(): Unit = doTest()

  def testUnitIfStatement(): Unit = doTest()

  def testWhileStatement(): Unit = doTest()

  def testSCL8580(): Unit = {
    doTest(
      s"""case class Filterable(s: List[String]) {
         |  def withFilter(p: List[String] => Boolean) = Monadic(s)
         |}
         |
         |case class Monadic(s: List[String]) {
         |  def map(f: List[String] => List[String]): Monadic = Monadic(f(s))
         |  def flatMap(f: List[String] => Monadic): Monadic = f(s)
         |  def foreach(f: List[String] => Unit): Unit = f(s)
         |  def withFilter(q: List[String] => Boolean): Monadic = this
         |}
         |
         |val filterable = Filterable(List("aaa"))
         |
         |${START}for (List(s) <- filterable) yield List(s, s)$END
         |
         |//Monadic""".stripMargin)
  }

}

class TypeInferenceStatementsTest_with_WithFilter_rewrite_in_for  extends TypeInferenceStatementsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= Scala_2_11

  def testForFilter(): Unit = doTest()
}