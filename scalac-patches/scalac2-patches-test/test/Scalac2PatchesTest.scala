import junit.framework.TestCase
import org.junit.Assert

class Scalac2PatchesTest extends TestCase {

  val compileTimeTest =
"""
""": "\n"

  def testNormalString(): Unit = Assert.assertTrue(
    "\r\n".length == 2)

  def testNormalInterpolatedString(): Unit = Assert.assertTrue(
    s"\r\n".length == 2)

  def testMultilineString(): Unit = Assert.assertTrue(
"""
""".length == 1)

  def testMultilineInterpolatedString(): Unit = Assert.assertTrue(
s"""
""".length == 1)

  def testCompileTime(): Unit = Assert.assertTrue(
s"""
${"\r\n"}""".length == 3)

}
