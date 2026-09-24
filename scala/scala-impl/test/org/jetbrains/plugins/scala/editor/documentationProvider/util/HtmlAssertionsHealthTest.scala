package org.jetbrains.plugins.scala.editor.documentationProvider.util

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.assertions.assertFails
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class HtmlAssertionsHealthTest extends TestCase with HtmlAssertions {

  @Test
  def `test assertDocHtml HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces`(): Unit = {
    assertDocHtml(
      "<body> some text </body>",
      "<body>  some   text  </body>",
      HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
    )

    assertDocHtml(
      "<body> some text <p> some text 1 </body>",
      "<body>  some text  <p>  some text 1  </body>",
      HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
    )
  }

  @Test
  def `test assertDocHtml HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces Failing`(): Unit = {
    assertFails {
      assertDocHtml(
        "<pre>preformatted</pre)",
        "<pre> preformatted </pre)",
        HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
      )
    }

    assertFails {
      assertDocHtml(
        "<pre>preformatted text  with   spaces</pre>)",
        "<pre>preformatted text  with spaces</pre>)",
        HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
      )
    }

    assertFails {
      assertDocHtml(
        """<pre>preformatted</pre)""",
        """<pre>
          |preformatted
          |</pre)""".stripMargin,
        HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
      )
    }
  }

  @Test
  def `test assertDocHtml HtmlSpacesComparisonMode.DontIgnore`(): Unit = {
    assertDocHtml(
      "<body>some text</body>",
      "<body>some text</body>",
      HtmlSpacesComparisonMode.DontIgnore
    )
  }

  @Test
  def `test assertDocHtml HtmlSpacesComparisonMode.DontIgnore Failing`(): Unit = {
    assertFails {
      assertDocHtml(
        "<body>some text</body>",
        "<body>some    text</body>",
        HtmlSpacesComparisonMode.DontIgnore
      )
    }
  }

  @Test
  def `test assertDocHtml HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces`(): Unit = {
    assertDocHtml(
      """<body>some
        |    text</body>""".stripMargin,
      """<body>some
        | text</body>""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces
    )
  }

  @Test
  def `test assertDocHtml HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces Failing`(): Unit = {
    assertFails {
      assertDocHtml(
        """<body>some
          |    text</body>""".stripMargin,
        """<body>some
          |
          | text</body>""".stripMargin,
        HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces
      )
    }
  }
}
