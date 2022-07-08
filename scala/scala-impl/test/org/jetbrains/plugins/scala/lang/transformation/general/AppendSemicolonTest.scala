package org.jetbrains.plugins.scala
package lang
package transformation
package general

class AppendSemicolonTest extends TransformerTest(new AppendSemicolon()) {

  def testSingleLineSeparator(): Unit = check(
    before =
      """A
        |B""".stripMargin,
    after =
      """A;
        |B;""".stripMargin
  )()

  def testMultipleLineSeparators(): Unit = check(
    before =
      """A
        |B""".stripMargin,
    after =
      """A;
        |B;""".stripMargin
  )()

  def testExplicit(): Unit = check(
    before =
      """A;
        |B;""".stripMargin,
    after =
      """A;
        |B;""".stripMargin
  )()
}
