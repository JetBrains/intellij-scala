package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

class Scala3FormatterCommentsTest extends Scala3FormatterBaseTest {

  private var oldKeepFirstColumnCommentSetting: Boolean = _

  override def setUp(): Unit= {
    super.setUp()
    oldKeepFirstColumnCommentSetting = getSettings.KEEP_FIRST_COLUMN_COMMENT
  }

  override def tearDown(): Unit = {
    getSettings.KEEP_FIRST_COLUMN_COMMENT = oldKeepFirstColumnCommentSetting
    super.tearDown()
  }

  private def doTextTestWithStripWithAllCommentTypes(before: String, after: String): Unit = {
    val beforeStripped = before.stripMargin
    val afterStripped = after.stripMargin
    doTextTest(beforeStripped, afterStripped)
    doTextTest(
      beforeStripped.replaceAll("// foo", "/* foo */"),
      afterStripped.replaceAll("// foo", "/* foo */")
    )
    doTextTest(
      beforeStripped.replaceAll("// foo", "/** foo */"),
      afterStripped.replaceAll("// foo", "/** foo */")
    )
  }

  private def doTextTestWithStripWithAllCommentTypes(text: String): Unit = {
    val stripped = text.stripMargin
    doTextTest(stripped)
    doTextTest(stripped.replaceAll("// foo", "/* foo */"))
    doTextTest(stripped.replaceAll("// foo", "/** foo */"))
  }

  def testIfThenElse(): Unit = doTextTestWithStripWithAllCommentTypes(
    """class A {
      |  if x < 0 then
      |    // foo
      |    "negative"
      |  else if x == 0 then
      |    // foo
      |    "zero"
      |  else
      |    // foo
      |    "positive"
      |}
      |"""
  )

  def testIfThenElse_KeepFirstColumnComment(): Unit = {
    getSettings.KEEP_FIRST_COLUMN_COMMENT = true
    doTextTestWithStripWithAllCommentTypes(
      """class A {
        |  if x < 0 then
        |// foo
        |    "negative"
        |  else if x == 0 then
        |// foo
        |    "zero"
        |  else
        |// foo
        |    "positive"
        |}
        |"""
    )
  }

  def testIfThenElse_DoNotKeepFirstColumnComment(): Unit = {
    getSettings.KEEP_FIRST_COLUMN_COMMENT = false
    doTextTestWithStripWithAllCommentTypes(
      """class A {
        |  if x < 0 then
        |// foo
        |    "negative"
        |  else if x == 0 then
        |// foo
        |    "zero"
        |  else
        |// foo
        |    "positive"
        |}
        |""",
      """class A {
        |  if x < 0 then
        |    // foo
        |    "negative"
        |  else if x == 0 then
        |    // foo
        |    "zero"
        |  else
        |    // foo
        |    "positive"
        |}
        |"""
    )
  }

  // SCL-20166
  def testExtension(): Unit = doTextTestWithStripWithAllCommentTypes(
    """extension (c: Circle)
      |  // foo
      |  def circumference: Double = c.radius * math.Pi * 2
      |"""
  )

  def testExtension_Object(): Unit = doTextTestWithStripWithAllCommentTypes(
    """object Example {
      |  case class Circle(x: Double, y: Double, radius: Double)
      |
      |  extension (c: Circle)
      |    // foo
      |    def circumference: Double = c.radius * math.Pi * 2
      |}
      |"""
  )
}
