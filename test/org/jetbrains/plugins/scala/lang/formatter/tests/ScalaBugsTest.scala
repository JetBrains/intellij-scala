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
}