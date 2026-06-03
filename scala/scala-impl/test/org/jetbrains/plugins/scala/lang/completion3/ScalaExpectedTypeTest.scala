package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionSortingTestBase
import org.junit.Test

class ScalaExpectedTypeTest extends ScalaCompletionSortingTestBase {

  override def getTestDataPath: String =
    super.getTestDataPath + "expectedType/"

  @Test
  def testFuncWithParam(): Unit =
    checkFirst("kurumba", "karamba")

  @Test
  def testStaticMethod(): Unit =
    checkFirst("foo", "faa")

  @Test
  def testAfterNew(): Unit =
    checkFirst("File")

  @Test
  def testStaticMethodParam(): Unit =
    checkFirst("int")

  @Test
  def testProjectionType(): Unit =
    checkFirst("Atest", "Btest")
}