package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaBlankLinesTest extends AbstractScalaFormatterTestBase with LineCommentsTestOps {

  def testSCL12353(): Unit = {
    cs.BLANK_LINES_AROUND_FIELD = 1

    val before =
      """
        |class A {
        |  val x = 1
        |
        |  val y = 2
        |
        |  def foo = {
        |    val a = 1
        |    println(a)
        |  }
        |}
      """.stripMargin
    doTextTestWithLineComments(before)
  }

  private val SCL12353Before =
    """
      |class SCL12353 {
      |  val x = 1
      |  val y = 2
      |  val z = 3
      |  def foo = {
      |    val a = 1
      |    println(a)
      |    val b = 1
      |    def boo = 42
      |    def goo = 22
      |    val c = 1
      |    val d = 1
      |  }
      |  def bar = 42
      |  val y1 = 13
      |  trait Inner {
      |    val x = 1
      |    val y = 2
      |    val z = 3
      |    def foo = {
      |      val a = 1
      |      println(a)
      |      val b = 1
      |      def boo = 42
      |      def goo = 22
      |      val c = 1
      |      val d = 1
      |    }
      |    def bar = 42
      |    val y1 = 13
      |  }
      |}
    """.stripMargin

  def testSCL12353_1(): Unit = {
    cs.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 1
    cs.BLANK_LINES_AROUND_FIELD = 1
    cs.BLANK_LINES_AROUND_METHOD = 1
    cs.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 1

    val after =
      """
        |class SCL12353 {
        |  val x = 1
        |
        |  val y = 2
        |
        |  val z = 3
        |
        |  def foo = {
        |    val a = 1
        |    println(a)
        |    val b = 1
        |
        |    def boo = 42
        |
        |    def goo = 22
        |
        |    val c = 1
        |    val d = 1
        |  }
        |
        |  def bar = 42
        |
        |  val y1 = 13
        |
        |  trait Inner {
        |    val x = 1
        |
        |    val y = 2
        |
        |    val z = 3
        |
        |    def foo = {
        |      val a = 1
        |      println(a)
        |      val b = 1
        |
        |      def boo = 42
        |
        |      def goo = 22
        |
        |      val c = 1
        |      val d = 1
        |    }
        |
        |    def bar = 42
        |
        |    val y1 = 13
        |  }
        |}
      """.stripMargin
    doTextTestWithLineComments(SCL12353Before, after)
  }

  def testSCL12353_2(): Unit = {
    cs.BLANK_LINES_AROUND_FIELD = 0
    cs.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 0
    ss.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES = 1

    cs.BLANK_LINES_AROUND_METHOD = 0
    cs.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 0
    ss.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = 0

    val after =
      """
        |class SCL12353 {
        |  val x = 1
        |  val y = 2
        |  val z = 3
        |  def foo = {
        |    val a = 1
        |
        |    println(a)
        |
        |    val b = 1
        |
        |    def boo = 42
        |    def goo = 22
        |
        |    val c = 1
        |
        |    val d = 1
        |  }
        |  def bar = 42
        |  val y1 = 13
        |
        |  trait Inner {
        |    val x = 1
        |    val y = 2
        |    val z = 3
        |    def foo = {
        |      val a = 1
        |
        |      println(a)
        |
        |      val b = 1
        |
        |      def boo = 42
        |      def goo = 22
        |
        |      val c = 1
        |
        |      val d = 1
        |    }
        |    def bar = 42
        |    val y1 = 13
        |  }
        |}
      """.stripMargin
    doTextTestWithLineComments(SCL12353Before, after)
  }

  def testSCL12353_3(): Unit = {
    cs.BLANK_LINES_AROUND_FIELD = 1
    cs.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 1

    ss.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES = 0

    cs.BLANK_LINES_AROUND_METHOD = 0
    cs.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 0
    ss.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = 0

    val after =
      """class SCL12353 {
        |  val x = 1
        |
        |  val y = 2
        |
        |  val z = 3
        |
        |  def foo = {
        |    val a = 1
        |    println(a)
        |    val b = 1
        |    def boo = 42
        |    def goo = 22
        |    val c = 1
        |    val d = 1
        |  }
        |  def bar = 42
        |
        |  val y1 = 13
        |
        |  trait Inner {
        |    val x = 1
        |
        |    val y = 2
        |
        |    val z = 3
        |
        |    def foo = {
        |      val a = 1
        |      println(a)
        |      val b = 1
        |      def boo = 42
        |      def goo = 22
        |      val c = 1
        |      val d = 1
        |    }
        |    def bar = 42
        |
        |    val y1 = 13
        |  }
        |}
      """.stripMargin
    doTextTestWithLineComments(SCL12353Before, after)
  }

  def testAfterClassHeader_Method(): Unit = {
    val with0Lines =
      """class A {
        |  def foo = 42
        |}
        |""".stripMargin

    val with1Lines =
      """class A {
        |
        |  def foo = 42
        |}
        |""".stripMargin

    val with2Lines =
      """class A {
        |
        |
        |  def foo = 42
        |}
        |""".stripMargin

    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 0
    getCommonSettings.BLANK_LINES_AROUND_METHOD = 0
    doTextTestWithLineComments(with0Lines, with0Lines)


    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 1
    doTextTestWithLineComments(with0Lines, with1Lines)
    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 2
    doTextTestWithLineComments(with0Lines, with2Lines)

    // around method shouldn't affect method after class header, like in Java
    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 0

    getCommonSettings.BLANK_LINES_AROUND_METHOD = 1
    doTextTestWithLineComments(with0Lines, with0Lines)
    getCommonSettings.BLANK_LINES_AROUND_METHOD = 2
    doTextTestWithLineComments(with0Lines, with0Lines)
  }

  def testAfterClassHeader_Class(): Unit = {
    val with0Lines =
      """class A {
        |  trait T
        |}
        |""".stripMargin

    val with1Lines =
      """class A {
        |
        |  trait T
        |}
        |""".stripMargin

    val with2Lines =
      """class A {
        |
        |
        |  trait T
        |}
        |""".stripMargin

    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 0
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 0
    doTextTestWithLineComments(with0Lines, with0Lines)

    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 1
    doTextTestWithLineComments(with0Lines, with1Lines)
    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 2
    doTextTestWithLineComments(with0Lines, with2Lines)

    // around class shouldn't affect inner class after class header, like in Java
    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 0

    getCommonSettings.BLANK_LINES_AROUND_CLASS = 1
    doTextTestWithLineComments(with0Lines, with0Lines)
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 2
    doTextTestWithLineComments(with0Lines, with0Lines)
  }

  def testAfterClassHeader_Field(): Unit = {
    val with0Lines =
      """class A {
        |  val field = 42
        |}
        |""".stripMargin

    val with1Line =
      """class A {
        |
        |  val field = 42
        |}
        |""".stripMargin

    val with2Lines =
      """class A {
        |
        |
        |  val field = 42
        |}
        |""".stripMargin

    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 0
    getCommonSettings.BLANK_LINES_AROUND_FIELD = 0
    doTextTestWithLineComments(with0Lines, with0Lines)

    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 1
    doTextTestWithLineComments(with0Lines, with1Line)
    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 2
    doTextTestWithLineComments(with0Lines, with2Lines)

    // around class shouldn't affect inner class after class header, like in Java
    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 0

    getCommonSettings.BLANK_LINES_AROUND_FIELD = 1
    doTextTestWithLineComments(with0Lines, with0Lines)
    getCommonSettings.BLANK_LINES_AROUND_FIELD = 2
    doTextTestWithLineComments(with0Lines, with0Lines)
  }
}
