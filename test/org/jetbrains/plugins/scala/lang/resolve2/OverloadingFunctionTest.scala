package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class OverloadingFunctionTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "overloading/functions/"
  }

  def testFunction1() = doTest()
  def testFunction2() = doTest()
  def testFunction3() = doTest()
  def testFunction4() = doTest()
}