package org.jetbrains.plugins.scala.editor.documentationProvider.util

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.assertions.assertFails

class HtmlAssertionsHealthTest extends TestCase with HtmlAssertions {

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

  def `test assertDocHtml HtmlSpacesComparisonMode.DontIgnore`(): Unit = {
    assertDocHtml(
      "<body>some text</body>",
      "<body>some text</body>",
      HtmlSpacesComparisonMode.DontIgnore
    )
  }

  def `test assertDocHtml HtmlSpacesComparisonMode.DontIgnore Failing`(): Unit = {
    assertFails {
      assertDocHtml(
        "<body>some text</body>",
        "<body>some    text</body>",
        HtmlSpacesComparisonMode.DontIgnore
      )
    }
  }

  def `test assertDocHtml HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces`(): Unit = {
    assertDocHtml(
      """<body>some
        |    text</body>""".stripMargin,
      """<body>some
        | text</body>""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces
    )
  }

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
