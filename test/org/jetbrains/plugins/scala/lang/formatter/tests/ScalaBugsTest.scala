package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

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

  def testSCL1875 {
    val before =
"""
/**
 * something{@link Foo}
 *something
 */
class A
""".replace("\r", "")
    val after =
"""
/**
 * something{@link Foo}
 * something
 */
class A
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL2066FromDiscussion {
    val settings = getSettings
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    val before =
"""
val n = Seq(1,2,3)
n.foreach
{
  x =>
  {
    println(x)
  }
}
""".replace("\r", "")
    val after =
"""
val n = Seq(1, 2, 3)
n.foreach
{
  x =>
  {
    println(x)
  }
}
""".replace("\r", "")
    doTextTest(before, after)
  }
}