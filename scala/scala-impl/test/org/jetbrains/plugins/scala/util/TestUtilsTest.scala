package org.jetbrains.plugins.scala.util

import junit.framework.TestCase
import org.junit.Assert.assertEquals

class TestUtilsTest extends TestCase {

  def testReadInputFromFileText(): Unit = {
    assertEquals(
      Seq(
        "aaa\naaa",
        "bbb\nbbb",
      ),
      TestUtils.readInputFromFileText(
        """aaa
          |aaa
          |------
          |bbb
          |bbb""".stripMargin
      )
    )
  }


  def testReadInputFromFileText_EmptyContentAfterLastSeparator(): Unit = {
    assertEquals(
      Seq(
        "aaa\naaa",
        "",
      ),
      TestUtils.readInputFromFileText(
        """aaa
          |aaa
          |-----""".stripMargin
      )
    )
  }

  def testReadInputFromFileText_ManyParts_EmptyContentAfterLastSeparator(): Unit = {
    assertEquals(
      Seq(
        "aaa\naaa",
        "bbb\nbbb",
        "ccc\nccc",
        "",
      ),
      TestUtils.readInputFromFileText(
        """aaa
          |aaa
          |-----
          |bbb
          |bbb
          |-----
          |ccc
          |ccc
          |-----""".stripMargin
      )
    )
  }

  def testReadInputFromFileText_ManyParts(): Unit = {
    assertEquals(
      Seq(
        "aaa\naaa",
        "bbb\nbbb",
        "ccc\nccc",
        "ddd\nddd",
      ),
      TestUtils.readInputFromFileText(
        """aaa
          |aaa
          |------
          |bbb
          |bbb
          |---------
          |ccc
          |ccc
          |---------------
          |ddd
          |ddd""".stripMargin
      )
    )
  }

  def testReadInputFromFileText_WithTrailingAndLeadingSpaces(): Unit = {
    assertEquals(
      Seq(
        "\n\naaa\naaa\n\n",
        "\n\nbbb\nbbb\n\n",
      ),
      TestUtils.readInputFromFileText(
        """
          |
          |aaa
          |aaa
          |
          |
          |------
          |
          |
          |bbb
          |bbb
          |
          |""".stripMargin
      )
    )
  }

  def testReadInputFromFileText_ManyParts_WithTrailingAndLeadingSpaces(): Unit = {
    assertEquals(
      Seq(
        "\naaa\naaa\n",
        "\nbbb\nbbb\n",
        "\n\nccc\nccc\n\n",
        "\n\nddd\nddd\n\n",
      ),
      TestUtils.readInputFromFileText(
        """
          |aaa
          |aaa
          |
          |------
          |
          |bbb
          |bbb
          |
          |---------
          |
          |
          |ccc
          |ccc
          |
          |
          |---------------
          |
          |
          |ddd
          |ddd
          |
          |""".stripMargin
      )
    )
  }
}
