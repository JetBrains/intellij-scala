package org.jetbrains.plugins.scala.lang.completion3

class ScalaExpectedTypeTest extends ScalaCompletionSortingTestCase {

  override def getTestDataPath: String =
    super.getTestDataPath + "expectedType/"

  def testFuncWithParam(): Unit =
    checkFirst("kurumba", "karamba")

  def testStaticMethod(): Unit =
    checkFirst("foo", "faa")

  def testAfterNew(): Unit =
    checkFirst("File")

  def testStaticMethodParam(): Unit =
    checkFirst("int")

  def testProjectionType(): Unit =
    checkFirst("Atest", "Btest")
}