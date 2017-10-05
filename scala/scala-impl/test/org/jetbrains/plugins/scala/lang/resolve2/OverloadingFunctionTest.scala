package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class OverloadingFunctionTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "overloading/functions/"
  }

  def testFunction1(): Unit = doTest()
  def testFunction2(): Unit = doTest()
  def testFunction3(): Unit = doTest()
  def testFunction4(): Unit = doTest()
  def testFunction5(): Unit = doTest()
  def testFunction6(): Unit = doTest()
}