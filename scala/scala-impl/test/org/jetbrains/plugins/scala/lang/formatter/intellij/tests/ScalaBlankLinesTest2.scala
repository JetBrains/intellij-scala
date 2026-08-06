package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaBlankLinesTest2 extends AbstractScalaFormatterTestBase with LineCommentsTestOps {

  def testSCL2477(): Unit =
    doTextTest(
      """
        |class Foo {
        |  //some comment
        |	private val i = 0;
        |
        |	/**
        |	 * @param p blah-blah-blah
        |	 */
        |	def doSmth(p: Int) {}
        |  //comment
        |  def foo = 1
        |}
        |""".stripMargin,
      """
        |class Foo {
        |  //some comment
        |  private val i = 0;
        |
        |  /**
        |   * @param p blah-blah-blah
        |   */
        |  def doSmth(p: Int) {}
        |
        |  //comment
        |  def foo = 1
        |}
        |""".stripMargin
    )

  def testSCL2477_1(): Unit =
    doTextTest(
      """class Foo {
        |  private val i = 0; //some comment
        |  private val j = 0;
        |
        |  /**
        |   * @param p blah-blah-blah
        |   */
        |  def doSmth(p: Int) {}
        |
        |  //comment
        |  def foo = 1
        |}
        |""".stripMargin
    )

  def testSCL4269(): Unit = {
    val before =
      """object HelloWorld { // A sample application object
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
        |""".stripMargin
    doTextTest(before)

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """object HelloWorld {
        |  // A sample application object
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
        |""".stripMargin
    )
  }

  def testSCL4269_1(): Unit = {
    val before =
      """object Test { // comment
        |}
        |
        |object Test { // comment
        |  val x = 42
        |}
        |
        |object Test { // comment
        |  def foo1 = ???
        |}
        |
        |object Test { // comment
        |  trait T
        |}
        |""".stripMargin
    doTextTest(before)

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """object Test {
        |  // comment
        |}
        |
        |object Test {
        |  // comment
        |  val x = 42
        |}
        |
        |object Test {
        |  // comment
        |  def foo1 = ???
        |}
        |
        |object Test {
        |  // comment
        |  trait T
        |}
        |""".stripMargin
    )
  }

  def testSCL4269_2_NoWhiteSpaceBeforeComment(): Unit = {
    val before =
      """object HelloWorld {// A sample application object (comment without before whitespace)
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
        |""".stripMargin

    doTextTest(
      before,
      """object HelloWorld { // A sample application object (comment without before whitespace)
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
        |""".stripMargin
    )

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """object HelloWorld {
        |  // A sample application object (comment without before whitespace)
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
        |""".stripMargin
    )
  }

  def testSCL4269_3(): Unit = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false
    doTextTest(
      """object HelloWorld { // A sample application object
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
        |""".stripMargin,
      """object HelloWorld {
        |  // A sample application object
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
        |""".stripMargin
    )
  }

  def testSCL15746(): Unit = {
    val after =
      """trait T {
        |  {
        |    val value3 = 555 //one-line comment
        |    val value4 = 666
        |  }
        |
        |  def foo = {
        |    val value3 = 555 //one-line comment
        |    val value4 = 666
        |  }
        |
        |  42 match {
        |    case 23 => {
        |      val value3 = 555 //one-line comment
        |      val value4 = 666
        |    }
        |    case _ =>
        |      val value3 = 555 //one-line comment
        |      lazy val value4 = 666
        |  }
        |
        |  Option(42).map { _ =>
        |    val value3 = 555 //one-line comment
        |    var value4 = 666
        |  }
        |}
        |""".stripMargin

    val before1 = after
    val before2 = removeSpacesBeforeLineComments(before1)

    doTextTest(before1, after)
    doTextTest(before2, after)
  }

  def testShouldRemoveSpaceBeforeSemicolon(): Unit = {
    val before1 =
      """class X {
        |  private val x = 0  ; //some comment
        |  private val y = 0 ;
        |  //some comment
        |  private val z = 0   ;
        |
        |  private val w = 0   ;
        |}
        |""".stripMargin
    val before2 = removeSpacesBeforeLineComments(before1)
    val after   =
      """class X {
        |  private val x = 0; //some comment
        |  private val y = 0;
        |  //some comment
        |  private val z = 0;
        |
        |  private val w = 0;
        |}
        |""".stripMargin
    doTextTest(before1, after)
    doTextTest(before2, after)
  }

  def testSCL6267(): Unit = {
    val before =
      """import net.liftweb.json.JsonDSL.{symbol2jvalue => _, _} // collision with Matcher's have 'symbol implicit
        |import java.util.UUID
        |""".stripMargin
    doTextTest(before)

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(before,
      """import net.liftweb.json.JsonDSL.{symbol2jvalue => _, _}
        |// collision with Matcher's have 'symbol implicit
        |import java.util.UUID
        |""".stripMargin
    )
  }

  def testSCL7898(): Unit = {
    getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = true

    val before =
      """class Test {
        |  println(a)
        |//  println(b)
        |}
        |""".stripMargin

    doTextTest(before)

    getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = false

    doTextTest(
      before,
      """class Test {
        |  println(a)
        |  //  println(b)
        |}
        |""".stripMargin
    )
  }

  def testSCL9321(): Unit =
    doTextTest(
      """// Multiple vars or vals not delimited by empty line will be wrapped
        |class WillBeWrapped1 {
        |  var a: Int = 0 // this comment will be wrapped
        |  val b: Int = 0 // this comment will be wrapped too
        |  var c: Int = 0 // this will not
        |}
        |
        |// Comments after statement before var/val will be wrapped
        |class WillBeWrapped2 {
        |  println() // this comment will be wrapped
        |  var a: Int = 0
        |
        |  println() // this comment will be wrapped too
        |
        |  val b: Int = 0
        |}
        |
        |class NoWrappings {
        |  def insideDefs() = {
        |    println() // test
        |    var x: Int = 0 // test
        |    var y: Int = 0 // test
        |  }
        |
        |  // var or val not after statements, must be delimited by empty line
        |  var a: Int = 0 // test
        |
        |  var b: Int = 0 // test
        |
        |  // single or multiple statements
        |  println() // test
        |  println() // test
        |}
        |""".stripMargin
    )

  def testCommentsAfterClassMembers(): Unit = {
    val before =
      """object Test { //comment0
        |  println(42) //comment1
        |  val x = 42 //comment2
        |  var y = 42 //comment3
        |  type F = Int //comment4
        |  def z = 42 //comment5
        |  class Inner //comment6
        |  object OInner //comment7
        |  trait TInner //comment8
        |}
        |""".stripMargin

    doTextTest(
      before,
      """object Test { //comment0
        |  println(42) //comment1
        |  val x = 42 //comment2
        |  var y = 42 //comment3
        |  type F = Int //comment4
        |
        |  def z = 42 //comment5
        |
        |  class Inner //comment6
        |
        |  object OInner //comment7
        |
        |  trait TInner //comment8
        |}
        |""".stripMargin
    )

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """object Test {
        |  //comment0
        |  println(42)
        |  //comment1
        |  val x = 42
        |  //comment2
        |  var y = 42
        |  //comment3
        |  type F = Int
        |  //comment4
        |
        |  def z = 42
        |  //comment5
        |
        |  class Inner
        |  //comment6
        |
        |  object OInner
        |  //comment7
        |
        |  trait TInner
        |  //comment8
        |}
        |""".stripMargin
    )
  }

  def testCommentsSurroundingClassMembers(): Unit = {
    val before =
      """class Test {
        |  //comment0
        |  println(42)
        |  //comment00
        |
        |  //comment1
        |  val x = 42
        |  //comment11
        |
        |  //comment2
        |  var y = 42
        |  //comment22
        |
        |  //comment3
        |  type F = Int
        |  //comment33
        |
        |  //comment4
        |  def z = 42
        |  //comment44
        |
        |  //comment5
        |  class Inner
        |  //comment55
        |
        |  //comment6
        |  object OInner
        |  //comment66
        |
        |  //comment7
        |  trait TInner
        |  //comment77
        |}
        |""".stripMargin

    scalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true
    doTextTest(before)
    scalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false
    doTextTest(before)
  }

  def testCommentsSurroundingClassMembers_DetachedCommentsShouldRemainDetached(): Unit = {
    getCommonSettings.BLANK_LINES_AROUND_FIELD = 1
    val before =
      """object Test {
        |  //comment0
        |  println(42)
        |  //comment00
        |  //comment1
        |  val x = 42
        |  //comment11
        |  //comment2
        |  var y = 42
        |  //comment22
        |  //comment3
        |  type F = Int
        |  //comment33
        |  //comment4
        |  def z = 42
        |  //comment44
        |  //comment5
        |  class Inner
        |  //comment55
        |  //comment6
        |  object OInner
        |  //comment66
        |  //comment7
        |  trait TInner
        |  //comment77
        |}
        |""".stripMargin
    val after  =
      """object Test {
        |  //comment0
        |  println(42)
        |
        |  //comment00
        |  //comment1
        |  val x = 42
        |
        |  //comment11
        |  //comment2
        |  var y = 42
        |
        |  //comment22
        |  //comment3
        |  type F = Int
        |
        |  //comment33
        |  //comment4
        |  def z = 42
        |
        |  //comment44
        |  //comment5
        |  class Inner
        |
        |  //comment55
        |  //comment6
        |  object OInner
        |
        |  //comment66
        |  //comment7
        |  trait TInner
        |  //comment77
        |}
        |""".stripMargin

    scalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true
    doTextTest(before, after)
    scalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false
    doTextTest(before, after)
  }

  def testCommentsAfterClassMembers_1(): Unit =
    doTextTestWithLineComments(
      """object Test {
        |  trait T
        |}
        |
        |object Test {
        |  trait T1
        |  trait T2
        |}
        |
        |object Test {
        |  trait T1
        |  val x = 42
        |}
        |
        |object Test {
        |  trait T1
        |  2 + 2
        |}
        |""".stripMargin,
      """object Test {
        |  trait T
        |}
        |
        |object Test {
        |  trait T1
        |
        |  trait T2
        |}
        |
        |object Test {
        |  trait T1
        |
        |  val x = 42
        |}
        |
        |object Test {
        |  trait T1
        |
        |  2 + 2
        |}
        |""".stripMargin
    )

  def testCommentsAfterClassMembers_2(): Unit = {
    val before =
      """object Test {
        |  trait T// comment
        |}
        |
        |object Test {
        |  trait T1 // comment
        |  trait T2 // comment
        |}
        |
        |object Test {
        |  trait T1// comment
        |  trait T2// comment
        |}
        |
        |object Test {
        |  trait T1 // comment
        |  val x = 42 // comment
        |}
        |
        |object Test {
        |  trait T1 // comment
        |  2 + 2 // comment
        |}
        |""".stripMargin

    doTextTest(
      before,
      """object Test {
        |  trait T // comment
        |}
        |
        |object Test {
        |  trait T1 // comment
        |
        |  trait T2 // comment
        |}
        |
        |object Test {
        |  trait T1 // comment
        |
        |  trait T2 // comment
        |}
        |
        |object Test {
        |  trait T1 // comment
        |
        |  val x = 42 // comment
        |}
        |
        |object Test {
        |  trait T1 // comment
        |
        |  2 + 2 // comment
        |}
        |""".stripMargin
    )

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """object Test {
        |  trait T
        |  // comment
        |}
        |
        |object Test {
        |  trait T1
        |  // comment
        |
        |  trait T2
        |  // comment
        |}
        |
        |object Test {
        |  trait T1
        |  // comment
        |
        |  trait T2
        |  // comment
        |}
        |
        |object Test {
        |  trait T1
        |  // comment
        |
        |  val x = 42
        |  // comment
        |}
        |
        |object Test {
        |  trait T1
        |  // comment
        |
        |  2 + 2
        |  // comment
        |}
        |""".stripMargin
    )
  }

  def testCommentsAfterClassMembers_3(): Unit =
    doTextTestWithLineComments(
      """object Test {
        |  def foo1 = ???
        |}
        |
        |object Test {
        |  def foo1 = ???
        |  def foo2 = ???
        |}
        |
        |object Test {
        |  def foo1 = ???
        |  trait T
        |}
        |
        |object Test {
        |  trait T
        |  def foo1 = ???
        |}
        |
        |object Test {
        |  def foo1 = ???
        |  val x = 42
        |}
        |
        |object Test {
        |  val x = 42
        |  def foo1 = ???
        |}
        |""".stripMargin,
      """object Test {
        |  def foo1 = ???
        |}
        |
        |object Test {
        |  def foo1 = ???
        |
        |  def foo2 = ???
        |}
        |
        |object Test {
        |  def foo1 = ???
        |
        |  trait T
        |}
        |
        |object Test {
        |  trait T
        |
        |  def foo1 = ???
        |}
        |
        |object Test {
        |  def foo1 = ???
        |
        |  val x = 42
        |}
        |
        |object Test {
        |  val x = 42
        |
        |  def foo1 = ???
        |}
        |""".stripMargin
    )

  def testCommentsAfterClassMembers_4(): Unit = {
    val before =
      """object Test {
        |  def foo1 = ??? // comment
        |}
        |
        |object Test {
        |  def foo1 = ???// comment
        |}
        |
        |object Test {
        |  def foo1 = ??? // comment
        |  def foo2 = ??? // comment
        |}
        |
        |object Test {
        |  def foo1 = ??? // comment
        |  trait T
        |}
        |
        |object Test {
        |  trait T
        |  def foo1 = ??? // comment
        |}
        |
        |object Test {
        |  def foo1 = ??? // comment
        |  val x = 42
        |}
        |
        |object Test {
        |  val x = 42
        |  def foo1 = ??? // comment
        |}
        |""".stripMargin

    doTextTest(
      before,
      """object Test {
        |  def foo1 = ??? // comment
        |}
        |
        |object Test {
        |  def foo1 = ??? // comment
        |}
        |
        |object Test {
        |  def foo1 = ??? // comment
        |
        |  def foo2 = ??? // comment
        |}
        |
        |object Test {
        |  def foo1 = ??? // comment
        |
        |  trait T
        |}
        |
        |object Test {
        |  trait T
        |
        |  def foo1 = ??? // comment
        |}
        |
        |object Test {
        |  def foo1 = ??? // comment
        |
        |  val x = 42
        |}
        |
        |object Test {
        |  val x = 42
        |
        |  def foo1 = ??? // comment
        |}
        |""".stripMargin
    )

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """object Test {
        |  def foo1 = ???
        |  // comment
        |}
        |
        |object Test {
        |  def foo1 = ???
        |  // comment
        |}
        |
        |object Test {
        |  def foo1 = ???
        |  // comment
        |
        |  def foo2 = ???
        |  // comment
        |}
        |
        |object Test {
        |  def foo1 = ???
        |  // comment
        |
        |  trait T
        |}
        |
        |object Test {
        |  trait T
        |
        |  def foo1 = ???
        |  // comment
        |}
        |
        |object Test {
        |  def foo1 = ???
        |  // comment
        |
        |  val x = 42
        |}
        |
        |object Test {
        |  val x = 42
        |
        |  def foo1 = ???
        |  // comment
        |}
        |""".stripMargin
    )
  }

  def testCommentsAfterClassMembers_5(): Unit = {
    val before =
      """object Test { //comment0
        |  println(42) //comment1
        |
        |  val x = 42 //comment2
        |
        |  var y = 42 //comment3
        |
        |  def z = 42 //comment4
        |
        |  type F = Int //comment5
        |
        |  class Inner //comment6
        |
        |  object OInner //comment7
        |
        |  trait TInner //comment8
        |}
        |""".stripMargin

    doTextTest(before)

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """object Test {
        |  //comment0
        |  println(42)
        |  //comment1
        |
        |  val x = 42
        |  //comment2
        |
        |  var y = 42
        |  //comment3
        |
        |  def z = 42
        |  //comment4
        |
        |  type F = Int
        |  //comment5
        |
        |  class Inner
        |  //comment6
        |
        |  object OInner
        |  //comment7
        |
        |  trait TInner
        |  //comment8
        |}
        |""".stripMargin
    )
  }

  def testCommentsAfterClass(): Unit =
    doTextTestWithLineComments(
      """trait Test
        |trait Test
        |
        |trait Test
        |trait Test
        |
        |trait Test
        |trait Test
        |""".stripMargin,
      """trait Test
        |
        |trait Test
        |
        |trait Test
        |
        |trait Test
        |
        |trait Test
        |
        |trait Test
        |""".stripMargin
    )

  def testCommentsAfterBlockStatements(): Unit = {
    val before =
      """def test = { //comment0
        |  println(42) //comment1
        |  val x = 42 //comment2
        |  var y = 42 //comment3
        |  type F = Int //comment4
        |  def z = 42 //comment5
        |  class Inner //comment6
        |  object OInner //comment7
        |  trait TInner
        |}
        |""".stripMargin

    doTextTest(
      before,
      """def test = { //comment0
        |  println(42) //comment1
        |  val x = 42 //comment2
        |  var y = 42 //comment3
        |  type F = Int //comment4
        |
        |  def z = 42 //comment5
        |
        |  class Inner //comment6
        |  object OInner //comment7
        |  trait TInner
        |}
        |""".stripMargin
    )

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """def test = {
        |  //comment0
        |  println(42)
        |  //comment1
        |  val x = 42
        |  //comment2
        |  var y = 42
        |  //comment3
        |  type F = Int
        |  //comment4
        |
        |  def z = 42
        |  //comment5
        |
        |  class Inner
        |  //comment6
        |  object OInner
        |  //comment7
        |  trait TInner
        |}
        |""".stripMargin
    )
  }

  def testCommentsAfterBlockStatements_1(): Unit =
    doTextTestWithLineComments(
      """def foo = {
        |  trait T
        |}
        |def foo = {
        |  trait T1
        |  trait T2
        |}
        |def foo = {
        |  trait T1
        |  val x = 42
        |}
        |def foo = {
        |  trait T1
        |  2 + 2
        |}
        |""".stripMargin
    )

  def testBlockCommentsAfterBlockStatements(): Unit = {
    val before =
      """object Test { /*comment0*/
        |  println(42) /*comment1*/
        |  val x = 42 /*comment2*/
        |  var y = 42 /*comment3*/
        |  type F = Int /*comment4*/
        |  def z = 42 /*comment5*/
        |  class Inner /*comment6*/
        |  object OInner /*comment7*/
        |  trait TInner
        |}
        |""".stripMargin

    val after =
      """object Test {
        |  /*comment0*/
        |  println(42)
        |  /*comment1*/
        |  val x = 42
        |  /*comment2*/
        |  var y = 42
        |  /*comment3*/
        |  type F = Int
        |
        |  /*comment4*/
        |  def z = 42
        |
        |  /*comment5*/
        |  class Inner
        |
        |  /*comment6*/
        |  object OInner
        |
        |  /*comment7*/
        |  trait TInner
        |}
        |""".stripMargin

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true
    doTextTest(before, after)
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false
    doTextTest(before, after)
  }

  def testSCL9516(): Unit = {
    val before =
      """if (false) { //comment
        |}
        |""".stripMargin

    doTextTest(before)

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """if (false) {
        |  //comment
        |}
        |""".stripMargin
    )
  }

  def testSCL9516_1(): Unit = {
    val before =
      """if (false) { //comment
        |  val x = 42
        |}
        |""".stripMargin

    doTextTest(before)

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """if (false) {
        |  //comment
        |  val x = 42
        |}
        |""".stripMargin
    )
  }

  def testSCL9516_2_NoWhiteSpaceBeforeComment(): Unit = {
    val before =
      """if (false) {//comment without before whitespace
        |  val x = 42
        |}
        |""".stripMargin

    doTextTest(
      before,
      """if (false) { //comment without before whitespace
        |  val x = 42
        |}
        |""".stripMargin
    )

    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false

    doTextTest(
      before,
      """if (false) {
        |  //comment without before whitespace
        |  val x = 42
        |}
        |""".stripMargin
    )
  }

  def testBlockCommentsAroundFunction(): Unit =
    doTextTest(
      """class a {
        |  /*before*/
        |  def foo(): Unit = ???
        |  /*after*/
        |}
        |
        |def other = {
        |  /*before*/
        |  def foo(): Unit = ???
        |  /*after*/
        |}
        |
        |/*before*/
        |def baz(): Unit = ???
        |/*after*/
        |""".stripMargin
    )

  def testBlockCommentsAroundFunction_1(): Unit =
    doTextTest(
      """class a {
        |  /*before*/
        |  def foo(): Unit = ???
        |  /*after*/
        |
        |  def other = ???
        |}
        |
        |def outer = {
        |  /*before*/
        |  def foo(): Unit = ???
        |  /*after*/
        |
        |  def other = ???
        |}
        |
        |/*before*/
        |def baz(): Unit = ???
        |/*after*/
        |
        |def other = ???
        |""".stripMargin
    )

  def testBlockCommentsAroundFunction2(): Unit =
    doTextTest(
      """class a {
        |  /*before*/ def foo(): Unit = ??? /*after*/
        |}
        |
        |def outer = {
        |  /*before*/ def foo(): Unit = ??? /*after*/
        |}
        |
        |/*before*/ def baz(): Unit = ??? /*after*/
        |""".stripMargin
    )

  def testBlockCommentsAroundFunction3(): Unit =
    doTextTest(
      """class a {
        |  /*before*/ def foo(): Unit = ??? /*after*/
        |
        |  def other = ???
        |}
        |
        |def outer = {
        |  /*before*/ def foo(): Unit = ??? /*after*/
        |
        |  def other = ???
        |}
        |
        |/*before*/ def baz(): Unit = ??? /*after*/
        |
        |def other = ???
        |""".stripMargin
    )

  def testLineCommentsAroundFunction(): Unit =
    doTextTest(
      """class a {
        |  //before
        |  def foo(): Unit = ???
        |  //after
        |}
        |
        |def outer = {
        |  //before
        |  def foo(): Unit = ???
        |  //after
        |}
        |
        |//before
        |def baz(): Unit = ???
        |//after
        |""".stripMargin
    )

  def testLineCommentsAroundFunction1(): Unit =
    doTextTest(
      """class a {
        |  //before
        |  def foo(): Unit = ???
        |  //after
        |
        |  def other = ???
        |}
        |
        |def outer = {
        |  //before
        |  def foo(): Unit = ???
        |  //after
        |
        |  def other = ???
        |}
        |
        |//before
        |def baz(): Unit = ???
        |//after
        |
        |def other = ???
        |""".stripMargin
    )

  def testBlockCommentsOnSingleLine(): Unit =
    doTextTest(
      """class a {
        |  /*1*/ 5 /*2*/ /*3*/
        |}
        |def foo = {
        |  /*1*/ 5 /*2*/ /*3*/
        |}
        |/*1*/ 5 /*2*/ /*3*/
        |""".stripMargin,
      """class a {
        |  /*1*/ 5 /*2*/
        |  /*3*/
        |}
        |
        |def foo = {
        |  /*1*/ 5 /*2*/
        |  /*3*/
        |}
        |/*1*/ 5 /*2*/
        |/*3*/
        |""".stripMargin
    )

  def testDocCommentsOnSingleLine(): Unit =
    doTextTest(
      """class a {
        |  /** 1 */ println(42) /** 2 */ /** 3 */
        |}
        |def foo(): Unit = {
        |  /** 1 */ println(42) /** 2 */ /** 3 */
        |}
        |/** 1 */ println(42) /** 2 */ /** 3 */
        |""".stripMargin,
      """class a {
        |  /** 1 */
        |  println(42)
        |
        |  /** 2 */
        |  /** 3 */
        |}
        |
        |def foo(): Unit = {
        |  /** 1 */
        |  println(42)
        |
        |  /** 2 */
        |  /** 3 */
        |}
        |
        |/** 1 */
        |println(42)
        |
        |/** 2 */
        |/** 3 */
        |""".stripMargin
    )

  def testLineCommentInFor(): Unit = {
    doTextTestWithLineComments(
      """for {
        |  x <- 1 to 2
        |  y <- 1 to 2
        |  y <- 1 to 2
        |} ???""".stripMargin
    )

    doTextTestWithLineComments(
      """for {
        |  x <- 1 to 2
        |  y <- 1 to 2
        |  y <- 1 to 2
        |} yield ???""".stripMargin
    )

    doTextTestWithLineComments(
      """for (
        |  x <- 1 to 2;
        |  y <- 1 to 2;
        |  y <- 1 to 2
        |) ???""".stripMargin
    )

    doTextTestWithLineComments(
      """for (
        |  x <- 1 to 2;
        |  y <- 1 to 2;
        |  y <- 1 to 2
        |) yield ???""".stripMargin
    )
  }

  def testRandomTest(): Unit = {
    val after =
      """//comment
        |trait T
        |""".stripMargin

    doTextTest(after)

    doTextTest(
      """  //comment
        |trait T
        |""".stripMargin,
      after
    )
  }

  def testRandomTest_1(): Unit = {
    doTextTest(
      """class A extends T1 // comment
        |  with T2
        |  with T3 {
        |  println()
        |}""".stripMargin
    )

    doTextTest(
      """class A extends T1
        |  with T2 // comment
        |  with T3 {
        |  println()
        |}""".stripMargin
    )

    doTextTest(
      """class A extends T1
        |  with T2
        |  with T3 // comment
        |{
        |  println()
        |}""".stripMargin
    )
  }

  def testDoNotMoveElseOnPreviousLineWithCommentWithoutSpecialTreatment(): Unit = {
    getCommonSettings.KEEP_LINE_BREAKS = false
    getCommonSettings.SPECIAL_ELSE_IF_TREATMENT = false

    doTextTest(
      """class A {
        |  if (true) {} // qwe
        |  else {}
        |
        |  if (true) {} // qwe
        |  else
        |    if (false) {} else
        |      if (false) {} else {}
        |}
        |""".stripMargin)
  }

  def testDoNotMoveElseOnPreviousLineWithCommentWithSpecialTreatment(): Unit = {
    getCommonSettings.KEEP_LINE_BREAKS = false
    getCommonSettings.SPECIAL_ELSE_IF_TREATMENT = true
    getCommonSettings.ELSE_ON_NEW_LINE = true

    doTextTest(
      """class A {
        |  if (true) {} // qwe
        |  else {}
        |
        |  if (true) {} // qwe
        |  else if (false) {}
        |  else if (false) {}
        |  else {}
        |}
        |""".stripMargin)
  }

  def testDoNotMoveElseOnPreviousLineWithCommentWithSpecialTreatment_1(): Unit = {
    getCommonSettings.KEEP_LINE_BREAKS = false
    getCommonSettings.SPECIAL_ELSE_IF_TREATMENT = true
    getCommonSettings.ELSE_ON_NEW_LINE = false

    doTextTest(
      """class A {
        |  if (true) {} // qwe
        |  else {}
        |
        |  if (true) {} // qwe
        |  else if (false) {} else if (false) {} else {}
        |}
        |""".stripMargin)
  }

  def testDoNotMoveCatchOnPreviousLineWithComment(): Unit = {
    getCommonSettings.KEEP_LINE_BREAKS = false

    doTextTest(
      """class A {
        |  try {
        |    42
        |  } // qwe
        |  catch {
        |    case _ =>
        |  }
        |}
        |""".stripMargin
    )
  }

  def testDoNotMoveWhileOnPreviousLineWithComment(): Unit = {
    getCommonSettings.KEEP_LINE_BREAKS = false

    doTextTest(
      """class A {
        |  do {
        |    42
        |  } // qwe
        |  while (true)
        |}
        |""".stripMargin
    )
  }

  def testDoNotMoveSelfTypeOnPreviousLineWithcomment(): Unit = {
    val before =
      """trait T { // comment
        |  self: Object =>
        |
        |}""".stripMargin
    scalaSettings.PLACE_SELF_TYPE_ON_NEW_LINE = true
    doTextTest(before)
    scalaSettings.PLACE_SELF_TYPE_ON_NEW_LINE = false
    doTextTest(before)
  }

  private val bracesData = Seq(
    """while (true) // comment
      |{
      |  42
      |}
      |""".stripMargin,
    """do // comment
      |{
      |  42
      |}
      |""".stripMargin,
    """for // comment
      |{i <- 1 to 5} println(i)
      |""".stripMargin,
    """if (true) // comment
      |{
      |  1
      |}
      |else 2
      |""".stripMargin,
    """if (true) // comment
      |{
      |  1
      |}
      |else // comment
      |{
      |  2
      |}""".stripMargin,
    """for {i <- 1 to 5} yield // comment
      |{
      |  i
      |}
      |""".stripMargin,
    """for {i <- 1 to 5} // comment
      |{
      |  println(i)
      |}
      |""".stripMargin,
    """try // comment
      |{
      |  42
      |}
      |catch // comment
      |{
      |  case _ =>
      |}
      |finally // comment
      |{
      |  42
      |}
      |""".stripMargin,
    """class A // comment
      |{
      |  42
      |}
      |""".stripMargin,
    """def foo = // comment
      |{
      |}
      |""".stripMargin,
    """Seq // comment
      |{
      |  1
      |}
      |  .map // comment
      |  { it => it * 2 }
      |""".stripMargin,
  )

  def testDoNotMoveBraceOnPreviousLineWithComment(): Unit =
    bracesData.foreach { before =>
      doTextTest(before, before)
    }

  def testDoNotMoveBraceOnPreviousLineWithComment_1(): Unit =
    bracesData.foreach { before =>
      val beforeNew = removeSpacesBeforeLineComments(before)
      doTextTest(beforeNew, before)
    }

  def testDoNotMoveBraceOnPreviousLineWithComment_2(): Unit = {
    commonSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    commonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    commonSettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE

    // when brace style is not default (END_OF_LINE) for-expression formatting looks strange
    //  not sure what to do with it, I guess will leave with this hoping that no one will change settings.BRACE_STYLE  ¯\_(ツ)_/¯
    val dataWithoutFor = bracesData.filter(_.startsWith("for"))
    dataWithoutFor.foreach { before =>
      val beforeNew = removeLineComments(before)
      val afterNew = beforeNew.replaceAll("\\s+\\{", "\n{") // enforce opening brace on new line
      doTextTest(beforeNew, afterNew)
    }
  }

  def testMoveBraceOnPreviousLineWithComment(): Unit = {
    bracesData.foreach { before =>
      val beforeNew = removeLineComments(before)
      val afterNew = beforeNew.replaceAll("\\s+\\{", " {") // place opening brace on previous line
      doTextTest(beforeNew, afterNew)
    }
  }

  def testForBracePlacement(): Unit = {
    val before =
      """for
        |{i <- 1 to 5} println(i)
        |
        |for
        |{i <- 1 to 5} yield i
        |
        |for
        |{i <- 1 to 5}
        |{ println(i) }
        |
        |for
        |{i <- 1 to 5} yield
        |{ i }
        |
        |for
        |{
        |  i <- 1 to 5
        |} println(i)
        |
        |for
        |{
        |  i <- 1 to 5
        |} yield i
        |
        |for (
        |  i <- 1 to 5
        |) yield i
        |
        |for
        |(
        |  i <- 1 to 5
        |) yield i
        |""".stripMargin

    doTextTest(
      before,
      """for {i <- 1 to 5} println(i)
        |
        |for {i <- 1 to 5} yield i
        |
        |for {i <- 1 to 5} {
        |  println(i)
        |}
        |
        |for {i <- 1 to 5} yield {
        |  i
        |}
        |
        |for {
        |  i <- 1 to 5
        |} println(i)
        |
        |for {
        |  i <- 1 to 5
        |} yield i
        |
        |for (
        |  i <- 1 to 5
        |) yield i
        |
        |for
        |(
        |  i <- 1 to 5
        |) yield i
        |""".stripMargin
    )

    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE

    doTextTest(
      before,
      """for
        |{i <- 1 to 5} println(i)
        |
        |for
        |{i <- 1 to 5} yield i
        |
        |for
        |{i <- 1 to 5}
        |{
        |  println(i)
        |}
        |
        |for
        |{i <- 1 to 5} yield
        |{
        |  i
        |}
        |
        |for
        |{
        |  i <- 1 to 5
        |} println(i)
        |
        |for
        |{
        |  i <- 1 to 5
        |} yield i
        |
        |for (
        |  i <- 1 to 5
        |) yield i
        |
        |for
        |(
        |  i <- 1 to 5
        |) yield i
        |""".stripMargin
    )

    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED

    doTextTest(
      before,
      """for
        |  {i <- 1 to 5} println(i)
        |
        |for
        |  {i <- 1 to 5} yield i
        |
        |for
        |  {i <- 1 to 5}
        |  {
        |  println(i)
        |  }
        |
        |for
        |  {i <- 1 to 5} yield
        |  {
        |  i
        |  }
        |
        |for
        |  {
        |  i <- 1 to 5
        |  } println(i)
        |
        |for
        |  {
        |  i <- 1 to 5
        |  } yield i
        |
        |for (
        |  i <- 1 to 5
        |) yield i
        |
        |for
        |(
        |  i <- 1 to 5
        |) yield i
        |""".stripMargin
    )

    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2

    doTextTest(
      before,
      """for
        |  {i <- 1 to 5} println(i)
        |
        |for
        |  {i <- 1 to 5} yield i
        |
        |for
        |  {i <- 1 to 5}
        |  {
        |    println(i)
        |  }
        |
        |for
        |  {i <- 1 to 5} yield
        |  {
        |    i
        |  }
        |
        |for
        |  {
        |    i <- 1 to 5
        |  } println(i)
        |
        |for
        |  {
        |    i <- 1 to 5
        |  } yield i
        |
        |for (
        |  i <- 1 to 5
        |) yield i
        |
        |for
        |(
        |  i <- 1 to 5
        |) yield i
        |""".stripMargin
    )
  }

  private def removeSpacesBeforeLineComments(text: String) =
    text.replaceAll(" +//", "//")

  private def removeLineComments(text: String) =
    text.replaceAll("//.*", "")

  def testSemicolonShouldNotAffectBlankLines(): Unit = {
    return // TODO: muted, fix SCL-19436, also test each setting separately

    val settings = getCommonSettings

    //settings.BLANK_LINES_BEFORE_PACKAGE // there cannot be any semicolon before package
    settings.BLANK_LINES_AFTER_PACKAGE = 2

    settings.BLANK_LINES_BEFORE_IMPORTS = 2
    settings.BLANK_LINES_AFTER_IMPORTS = 2
    settings.BLANK_LINES_AROUND_CLASS = 2
    settings.BLANK_LINES_AROUND_FIELD = 2
    settings.BLANK_LINES_AROUND_METHOD = 2
    settings.BLANK_LINES_BEFORE_METHOD_BODY = 2
    settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 2
    settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE=2
    settings.BLANK_LINES_AFTER_CLASS_HEADER=2
    settings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER=2
    settings.BLANK_LINES_BEFORE_CLASS_END=2
    getScalaSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES=2
    getScalaSettings.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES=2
    getScalaSettings.BLANK_LINES_AROUND_CLASS_IN_INNER_SCOPES=2

    val before =
      """package a.b.c
        |import x.y.z
        |class A {
        |  def foo = 1
        |  val x = 1
        |  import x.y.z
        |  def method = {
        |    val x = 1
        |    def foo = 1
        |    class A
        |    import x.y.z
        |  }
        |}
        |trait T {
        |  def foo = 1
        |  val x = 1
        |  import x.y.z
        |  new Anonymous {
        |    def foo= 1
        |  }
        |}
        |""".stripMargin
    val after =
      """package a.b.c
        |
        |
        |import x.y.z
        |
        |
        |class A {
        |
        |
        |  def foo = 1
        |
        |
        |  val x = 1
        |
        |
        |  import x.y.z
        |
        |
        |  def method = {
        |
        |
        |    val x = 1
        |
        |
        |    def foo = 1
        |
        |
        |    class A
        |    import x.y.z
        |  }
        |
        |
        |}
        |
        |
        |trait T {
        |
        |
        |  def foo = 1
        |
        |
        |  val x = 1
        |
        |
        |  import x.y.z
        |
        |
        |  new Anonymous {
        |
        |
        |    def foo = 1
        |  }
        |
        |
        |}
        |""".stripMargin

    doTextTest(
      before,
      after
    )

    def placeAfterEachNonEmptyLine(text: String, lineSuffix: String): String =
      text.linesIterator.map(x => if(x.trim.isEmpty) x else x + lineSuffix).mkString("\n")

    doTextTest(
      placeAfterEachNonEmptyLine(before, ";"),
      placeAfterEachNonEmptyLine(after, ";")
    )

    doTextTest(
      placeAfterEachNonEmptyLine(before, " ; ;; ; ;;;  "),
      placeAfterEachNonEmptyLine(after, ";;;;;;;")
    )

    doTextTest(
      placeAfterEachNonEmptyLine(before, " ; ;; ; ;;;  // comment"),
      placeAfterEachNonEmptyLine(after, ";;;;;;; // comment")
    )
  }

  def testLineCommentBetweenClassNameAncExtendsList(): Unit = {
    doTextTest(
      """class MyClass1 //line comment for constructor
        |{
        |}
        |
        |class MyClass2
        |//line comment for constructor
        |{
        |}
        |
        |class MyClass3(x: Int) //line comment for constructor
        |{
        |}
        |
        |class MyClass4(x: Int)
        |//line comment for constructor
        |{
        |}
        |""".stripMargin
    )
  }
}