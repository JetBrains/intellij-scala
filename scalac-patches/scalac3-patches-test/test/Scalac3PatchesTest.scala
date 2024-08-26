import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Scalac3PatchesTest:

  @Test
  def normalString(): Unit = assertTrue(
    "\r\n".length == 2)

  @Test
  def normalInterpolatedString(): Unit = assertTrue(
    s"\r\n".length == 2)

  @Test
  def multilineString(): Unit = assertTrue(
"""
""".length == 1)

  @Test
  def multilineInterpolatedString(): Unit = assertTrue(
s"""
""".length == 1)

  @Test
  def compileTime(): Unit = assertTrue(
s"""
${"\r\n"}""".length == 3)
