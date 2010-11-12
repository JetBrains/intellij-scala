package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaBugsTest extends AbstractScalaFormatterTestBase {
  /* stub:
  def test {
    val before =
"""
""".replace("\r", "")
    val after =
"""
""".replace("\r", "")
    doTextTest(before, after)
  }
   */

  def testSCL2424 {
    val before =
"""
someMethod(new Something, abc, def)
""".replace("\r", "")
    val after =
"""
someMethod(new Something, abc, def)
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL2425 {
    val before =
"""
import foo.{Foo, Bar}
""".replace("\r", "")
    val after =
"""
import foo.{Foo, Bar}
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL2477 {
    val before =
"""
class Foo {
  //some comment
	private val i = 0;

	/**
	 * @param p blah-blah-blah
	 */
	def doSmth(p: Int) {}
  //comment
  def foo = 1
}
""".replace("\r", "")
    val after =
"""
class Foo {
  //some comment
  private val i = 0;

  /**
   * @param p blah-blah-blah
   */
  def doSmth(p: Int) {}

  //comment
  def foo = 1
}
""".replace("\r", "")
    doTextTest(before, after)
  }
}