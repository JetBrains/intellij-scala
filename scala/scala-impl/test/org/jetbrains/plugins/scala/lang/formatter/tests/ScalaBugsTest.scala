package org.jetbrains.plugins.scala.lang.formatter.tests

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaBugsTest extends AbstractScalaFormatterTestBase {
  /* stub:
  def test {
    val before =
"""
"""
    val after =
"""
"""
    doTextTest(before, after)
  }
   */

  def testSCL2424() {
    val before =
"""
someMethod(new Something, abc, def)
"""

    doTextTest(before)
  }

  def testSCL2425() {
    val before =
"""
import foo.{Foo, Bar}
"""

    doTextTest(before)
  }

  def testSCL2477() {
    val before =
"""
class Foo {
  //some comment
	private val i = 0;

	/**
	 * @param p blah-blah-blah
	 */
	def doSmth(p: Int) {}
  //comment
  def foo = 1
}
"""
    val after =
"""
class Foo {
  //some comment
  private val i = 0;

  /**
    * @param p blah-blah-blah
    */
  def doSmth(p: Int) {}

  //comment
  def foo = 1
}
"""
    doTextTest(before, after)
  }

  def testSCL1875() {
    val before =
"""
/**
 * something{@link Foo}
 *something
 */
class A
"""
    val after =
"""
/**
  * something{@link Foo}
  * something
  */
class A
"""
    doTextTest(before, after)
  }

  def testSCL2066FromDiscussion() {
    val settings = getCommonSettings
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    val before =
"""
val n = Seq(1,2,3)
n.foreach
{
  x =>
  {
    println(x)
  }
}
"""
    val after =
"""
val n = Seq(1, 2, 3)
n.foreach
{
  x =>
  {
    println(x)
  }
}
"""
    doTextTest(before, after)
  }

  def testSCL2775sTrue() {
    getScalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = true

    val before =
"""
Set(1, 2, 3).filter{a => a % 2 == 0}
List((1, 2), (2, 3), (3, 4)).map {case (k: Int, n: Int) => k + n}
Map(1 -> "aa", 2 -> "bb", 3 -> "cc").filter{ case (1, "aa") => true; case _ => false}
"""
    val after =
"""
Set(1, 2, 3).filter { a => a % 2 == 0 }
List((1, 2), (2, 3), (3, 4)).map { case (k: Int, n: Int) => k + n }
Map(1 -> "aa", 2 -> "bb", 3 -> "cc").filter { case (1, "aa") => true; case _ => false }
"""

    doTextTest(before, after)
  }

  def testSCL2775sFalse() {
    getScalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = false
    getScalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = true

    val before =
"""
Set(1, 2, 3).filter{a => a % 2 == 0}
List((1, 2), (2, 3), (3, 4)).map {case (k: Int, n: Int) => k + n}
Map(1 -> "aa", 2 -> "bb", 3 -> "cc").filter{ case (1, "aa") => true; case _ => false}
"""

    val after =
"""
Set(1, 2, 3).filter {
  a => a % 2 == 0
}
List((1, 2), (2, 3), (3, 4)).map {
  case (k: Int, n: Int) => k + n
}
Map(1 -> "aa", 2 -> "bb", 3 -> "cc").filter {
  case (1, "aa") => true;
  case _ => false
}
"""

    doTextTest(before, after)
  }

  def testSCL2839sTrue() {
    getScalaSettings.SPACES_IN_ONE_LINE_BLOCKS = true
    getCommonSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true

    val before =
"""
def func() {println("test")}

def func2() {
  println("test")}
"""

    val after =
"""
def func() { println("test") }

def func2() {
  println("test")
}
"""

    doTextTest(before, after)
  }

  def testSCL2839sFalse() {
    getCommonSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false

    val before =
"""
def func() {  println()}

def func2() { println()
}
"""

    val after =
"""
def func() {
  println()
}

def func2() {
  println()
}
"""

    doTextTest(before, after)
  }

  def testSCL2470() {
    getScalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS = true

    val before =
"""
def m = {
  () => 123
}

def m2 = {
  () => {
    123
  }
}

def f[T](i: Int) {
    val a = () => 123
}

(a: Int, b: Int, c: Int) => a + b + c
"""

    val after =
"""
def m = {
  () => 123
}

def m2 = {
  () => {
    123
  }
}

def f[T](i: Int) {
  val a = () => 123
}

(a: Int, b: Int, c: Int) => a + b + c
"""

    doTextTest(before, after)
  }

  def testSCL3126AllTrue() {
    getScalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES = true
    getScalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME = true
    getCommonSettings.SPACE_BEFORE_METHOD_PARENTHESES = true

    val before =
"""
def f() {
  println()
}

def foo (){}

def g(): Int = 12

def gg(i: Int): Int = {
  i*2
}

def test (i: Int) {}

def +++(s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
"""

    val after =
"""
def f () {
  println()
}

def foo () {}

def g (): Int = 12

def gg (i: Int): Int = {
  i * 2
}

def test (i: Int) {}

def +++ (s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
"""

    doTextTest(before, after)
  }

  def testSCL3126InfixFalse() {
    getScalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES = false
    getScalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME = true
    getCommonSettings.SPACE_BEFORE_METHOD_PARENTHESES = true

    val before =
"""
def f() {
  println()
}

def foo (){}

def g(): Int = 12

def gg(i: Int): Int = {
  i*2
}

def test (i: Int) {}

def +++(s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
      """

    val after =
      """
def f () {
  println()
}

def foo () {}

def g (): Int = 12

def gg (i: Int): Int = {
  i * 2
}

def test (i: Int) {}

def +++ (s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
"""

    doTextTest(before, after)
  }

  def testSCL3126InfixTruePreservevTrue() {
    getScalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES = true
    getScalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME = true
    getCommonSettings.SPACE_BEFORE_METHOD_PARENTHESES = false

    val before =
"""
def f() {
  println()
}

def foo (){}

def g(): Int = 12

def gg(i: Int): Int = {
  i*2
}

def test (i: Int) {}

def +++(s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
      """

    val after =
      """
def f() {
  println()
}

def foo () {}

def g(): Int = 12

def gg(i: Int): Int = {
  i * 2
}

def test (i: Int) {}

def +++ (s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
"""

    doTextTest(before, after)
  }

  def testSCL3126InfixTruePreserveFalse() {
    getScalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES = true
    getScalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME = false
    getCommonSettings.SPACE_BEFORE_METHOD_PARENTHESES = false

    val before =
"""
def f() {
  println()
}

def foo (){}

def g(): Int = 12

def gg(i: Int): Int = {
  i*2
}

def test (i: Int) {}

def +++(s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
      """

    val after =
      """
def f() {
  println()
}

def foo() {}

def g(): Int = 12

def gg(i: Int): Int = {
  i * 2
}

def test(i: Int) {}

def +++ (s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
"""

    doTextTest(before, after)
  }
  def testSCL3126AllFalse() {
    getScalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES = false
    getScalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME = false
    getCommonSettings.SPACE_BEFORE_METHOD_PARENTHESES = false

    val before =
"""
def f() {
  println()
}

def foo (){}

def g(): Int = 12

def gg(i: Int): Int = {
  i*2
}

def test (i: Int) {}

def +++(s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::= (o: Any) {}
      """

    val after =
      """
def f() {
  println()
}

def foo() {}

def g(): Int = 12

def gg(i: Int): Int = {
  i * 2
}

def test(i: Int) {}

def +++(s: StringBuilder): StringBuilder = {
  s append this.toString
}

def ::=(o: Any) {}
"""

    doTextTest(before, after)
  }

  def testSCL2474() {
    getCommonSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    getCommonSettings.SPACE_BEFORE_METHOD_PARENTHESES = true

    val before =
"""
def f(i: Int)(j: Int) {}

f(1)(2)
"""

    val after =
"""
def f (i: Int)(j: Int) {}

f (1)(2)
"""

    doTextTest(before, after)
  }

  def testThisExtraSpace() {
    getCommonSettings.SPACE_BEFORE_METHOD_PARENTHESES = false
    getCommonSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = false

    val before =
"""
class A(i: Int) {
  def this(s: String) {
    this (s.length)
  }

  def this () {
    this("")
  }
}

class B(i: Int)(s: String) {
  def this(s: String) {
    this(s.length)(s)
  }

  def this () {
    this ("")
  }
}
"""

    val after =
"""
class A(i: Int) {
  def this(s: String) {
    this(s.length)
  }

  def this() {
    this("")
  }
}

class B(i: Int)(s: String) {
  def this(s: String) {
    this(s.length)(s)
  }

  def this() {
    this("")
  }
}
"""

    doTextTest(before, after)
  }

  def testSpaceInsideClosureBraces() {
    getScalaSettings.SPACE_INSIDE_CLOSURE_BRACES = true
    getScalaSettings.SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES = true
    getScalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = true
    getScalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = false
    getCommonSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
    val before =
      """
Array.fill(34){scala.util.Random.nextInt(12)  }

foos map{ t=>getCounts(t).toSeq sortBy {-_._2 }   map {_._1 }}

bars foreach {case  (x, y) =>
  list.add(x + y)
}

bars  foreach {
  case (x,y)   => list.add(x+y)
}

bars foreach{ case (x,y) =>   list.add(x + y) }

      """

    val after =
      """
Array.fill(34) { scala.util.Random.nextInt(12) }

foos map { t => getCounts(t).toSeq sortBy { -_._2 } map { _._1 } }

bars foreach { case (x, y) =>
  list.add(x + y)
}

bars foreach {
  case (x, y) => list.add(x + y)
}

bars foreach { case (x, y) => list.add(x + y) }

      """

    doTextTest(before, after)
  }

  def testNoSpaceInsideClosure() {
    getScalaSettings.SPACE_INSIDE_CLOSURE_BRACES = false
    getScalaSettings.SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES = true
    getScalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = true
    getScalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = false
    getCommonSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
    val before =
      """
Array.fill(34){scala.util.Random.nextInt(12)  }

foos map{ t=>getCounts(t).toSeq sortBy {-_._2 }   map {_._1 }}

bars foreach {case  (x, y) =>
  list.add(x + y)
}

bars  foreach {
  case (x,y)   => list.add(x+y)
}

bars foreach{ case (x,y) =>   list.add(x + y) }

      """

    val after =
      """
Array.fill(34) {scala.util.Random.nextInt(12)}

foos map {t => getCounts(t).toSeq sortBy {-_._2} map {_._1}}

bars foreach {case (x, y) =>
  list.add(x + y)
}

bars foreach {
  case (x, y) => list.add(x + y)
}

bars foreach {case (x, y) => list.add(x + y)}
      """

    doTextTest(before, after)
  }

  def testSCL6702() {
    getCurrentCodeStyleSettings.FORMATTER_TAGS_ENABLED = true
    val before =
    """
      |//@formatter:off
      |class SCL6702 {
      |  def foo(p: String ) {
      |    println(p )
      |  }
      |
      |  //@formatter:on
      |  def foop(p: String ): Unit = {
      |    println(p )
      |  }
      |}
    """.stripMargin

    val after =
    """
      |//@formatter:off
      |class SCL6702 {
      |  def foo(p: String ) {
      |    println(p )
      |  }
      |
      |  //@formatter:on
      |  def foop(p: String): Unit = {
      |    println(p)
      |  }
      |}
    """.stripMargin

    doTextTest(before, after)
  }

  def testSCL5488_1() {
    getScalaSettings.SPACES_IN_ONE_LINE_BLOCKS = false
    getScalaSettings.SPACE_INSIDE_CLOSURE_BRACES = false
    getCommonSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true

    val before =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map {t => t.toSeq sortBy {-_ } map { _ * 2} }
        |  val f4: (Int, Int) => Int = { _ + _}
        |  val f5: (Int, Int) => Int = {_ + _ }
        |}
      """.stripMargin

    val after =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map {t => t.toSeq sortBy {-_} map {_ * 2}}
        |  val f4: (Int, Int) => Int = {_ + _}
        |  val f5: (Int, Int) => Int = {_ + _}
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL5488_2() {
    getScalaSettings.SPACES_IN_ONE_LINE_BLOCKS = true
    getScalaSettings.SPACE_INSIDE_CLOSURE_BRACES = false
    getCommonSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true

    val before =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map {t => t.toSeq sortBy {-_ } map { _ * 2} }
        |  val f4: (Int, Int) => Int = { _ + _}
        |  val f5: (Int, Int) => Int = {_ + _ }
        |}
      """.stripMargin

    val after =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map { t => t.toSeq sortBy { -_ } map { _ * 2 } }
        |  val f4: (Int, Int) => Int = { _ + _ }
        |  val f5: (Int, Int) => Int = { _ + _ }
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL5488_3() {
    getScalaSettings.SPACES_IN_ONE_LINE_BLOCKS = false
    getScalaSettings.SPACE_INSIDE_CLOSURE_BRACES = true
    getCommonSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true

    val before =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map {t => t.toSeq sortBy {-_ } map { _ * 2} }
        |  val f4: (Int, Int) => Int = { _ + _}
        |  val f5: (Int, Int) => Int = {_ + _ }
        |}
      """.stripMargin

    val after =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map { t => t.toSeq sortBy {-_} map {_ * 2} }
        |  val f4: (Int, Int) => Int = {_ + _}
        |  val f5: (Int, Int) => Int = {_ + _}
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL5488_4() {
    getScalaSettings.SPACES_IN_ONE_LINE_BLOCKS = true
    getScalaSettings.SPACE_INSIDE_CLOSURE_BRACES = true
    getCommonSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true

    val before =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map {t => t.toSeq sortBy {-_ } map { _ * 2} }
        |  val f4: (Int, Int) => Int = { _ + _}
        |  val f5: (Int, Int) => Int = {_ + _ }
        |}
      """.stripMargin

    val after =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map { t => t.toSeq sortBy { -_ } map { _ * 2 } }
        |  val f4: (Int, Int) => Int = { _ + _ }
        |  val f5: (Int, Int) => Int = { _ + _ }
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL9243() {
    getScalaSettings.INDENT_BRACED_FUNCTION_ARGS = false
    val before =
      """
        |class a {
        |  foo(
        |  {
        |    "b" + "a" + "r"
        |  }
        |  )
        |}
      """.stripMargin

    doTextTest(before)
  }

  def testSCL5427(): Unit = {
    getScalaSettings.USE_SCALADOC2_FORMATTING = false

    val before =
      """
        |/**
        |  * Some comments
        |  */
        |class A
      """.stripMargin

    val after =
      """
        |/**
        | * Some comments
        | */
        |class A
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL9264(): Unit = {
    val before =
      """
        |class X {
        |  (for {
        |    i <- 1 to 10
        |  } yield {
        |      1
        |  }).map(_ + 1)
        |}
      """.stripMargin

    val after =
      """
        |class X {
        |  (for {
        |    i <- 1 to 10
        |  } yield {
        |    1
        |  }).map(_ + 1)
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL7898(): Unit = {
    getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = true

    val before =
      """
        |class Test {
        |  println(a)
        |//  println(b)
        |}
      """.stripMargin

    doTextTest(before)
  }

  def testSCL9387(): Unit = {
    val before =
      """
        |val x = for {
        |//Comment
        |  x <- Nil
        |} yield {
        |    x
        |  }
      """.stripMargin

    val after =
      """
        |val x = for {
        |  //Comment
        |  x <- Nil
        |} yield {
        |  x
        |}
      """.stripMargin

    /* TODO this is only a temporary reference
      actual result should be the following:
      |val x = for {
      |  //Comment
      |  x <- Nil
      |} yield {
      |  x
      |}
      But current implementation of formatting model does not provide reasonable means of implementing this case.
     */

    doTextTest(before, after)
  }

  def testSCL5028_1(): Unit = {
    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE

    val before =
      """
        |try {
        |  expr
        |} catch
        |{
        |  case _: Throwable => println("gotcha!")
        |}
      """.stripMargin

    val after =
      """
        |try
        |{
        |  expr
        |} catch
        |{
        |  case _: Throwable => println("gotcha!")
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL5028_2(): Unit = {
    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    getCommonSettings.CATCH_ON_NEW_LINE = true

    val before =
      """
        |try {
        |  expr
        |} catch
        |{
        |  case _: Throwable => println("gotcha!")
        |}
      """.stripMargin

    val after =
      """
        |try
        |  {
        |    expr
        |  }
        |catch
        |  {
        |    case _: Throwable => println("gotcha!")
        |  }
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL8825(): Unit = {
    getScalaSettings.DO_NOT_INDENT_CASE_CLAUSE_BODY = true

    val before =
      """
        |{
        |  case (i) =>
        |  testExpr
        |}
      """.stripMargin

    doTextTest(before)
  }

  def testSCL2454(): Unit = {
    getCommonSettings.KEEP_LINE_BREAKS = false

    val before =
      """
        |val v
        |    =
        |    "smth"
      """.stripMargin

    val after =
      """
        |val v = "smth"
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL2468(): Unit = {
    getScalaSettings.NEWLINE_AFTER_ANNOTATIONS = true

    val before =
      """
        |@throws(classOf[IOException]) @deprecated def doSmth() {}
      """.stripMargin

    val after =
      """
        |@throws(classOf[IOException])
        |@deprecated
        |def doSmth() {}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL2469(): Unit = {
    getCommonSettings.VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    val before =
      """
        |class Test {
        |  def foo(): Unit = {
        |    @deprecated @deprecated
        |    val myLocalVal = 42
        |  }
        |}
      """.stripMargin

    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    @deprecated
        |    @deprecated
        |    val myLocalVal = 42
        |  }
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL2571(): Unit = {
    getCommonSettings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    val before =
      """
        |class Foo extends Object with Thread with Serializable {
        |  def foo(x: Int = 0,
        |          y: Int = 1,
        |          z: Int = 2) = new Foo
        |}
      """.stripMargin

    val after =
      """
        |class Foo extends Object with
        |  Thread with
        |  Serializable {
        |  def foo(x: Int = 0,
        |          y: Int = 1,
        |          z: Int = 2) = new Foo
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL2571_1(): Unit = {
    getCommonSettings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    val before =
      """
        |class Foo extends Object with Thread {
        |  def foo(x: Int = 0,
        |          y: Int = 1,
        |          z: Int = 2) = new Foo
        |}
      """.stripMargin

    val after =
      """
        |class Foo
        |  extends Object with Thread {
        |  def foo(x: Int = 0,
        |          y: Int = 1,
        |          z: Int = 2) = new Foo
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL2571_2(): Unit = {
    getCommonSettings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    getCommonSettings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    val before =
      """
        |class Foo extends Object with Thread with Serializable {
        |  def foo(x: Int = 0,
        |          y: Int = 1,
        |          z: Int = 2) = new Foo
        |}
      """.stripMargin

    val after =
      """
        |class Foo
        |  extends Object with
        |    Thread with
        |    Serializable {
        |  def foo(x: Int = 0,
        |          y: Int = 1,
        |          z: Int = 2) = new Foo
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL2999(): Unit = {
    getCommonSettings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    getScalaSettings.WRAP_BEFORE_WITH_KEYWORD = true
    getCommonSettings.getIndentOptions.CONTINUATION_INDENT_SIZE = 4

    val before =
      """
        |class MyLongClassName(someParam: String, someOtherParam: Int) extends SomeClass with SomeTrait with AnotherTrait with AndAnotherTrait with YetAnotherTrait {
        |}
      """.stripMargin

    val after =
      """
        |class MyLongClassName(someParam: String, someOtherParam: Int) extends SomeClass
        |    with SomeTrait
        |    with AnotherTrait
        |    with AndAnotherTrait
        |    with YetAnotherTrait {
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL3140_disabled(): Unit = {
    getScalaSettings.ENABLE_SCALADOC_FORMATTING = false

    val before =
      """
        |/**
        |  *    Pooly formatted scalaDoc.
        |    *Will still be formatted poorly.
        |
        |*                If formatting
        |   is disabled.
        |  Asterisks will be aligned and added though, like in java.
        |   */
      """.stripMargin

    val after =
      """
        |/**
        |  *    Pooly formatted scalaDoc.
        |  *Will still be formatted poorly.
        |  *
        |  *                If formatting
        |  *is disabled.
        |  *Asterisks will be aligned and added though, like in java.
        |  */
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL3140_noAlignment(): Unit = {

    getScalaSettings.SD_ALIGN_RETURN_COMMENTS = false

    val before =
      """
        |/**
        |  * Foos the given x, returning foo'ed x.
        |  *
        |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *         eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @throws RuntimeException whenever it feels like it
        |  */
      """.stripMargin

    val after =
      """
        |/**
        |  * Foos the given x, returning foo'ed x.
        |  *
        |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  * eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @throws RuntimeException whenever it feels like it
        |  */
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL3140_addBlankLineTag(): Unit = {
    getScalaSettings.SD_BLANK_LINE_AFTER_PARAMETERS_COMMENTS = true

    val before =
      """
        |/**
        |  * Foos the given x, returning foo'ed x.
        |  *
        |  * @param x Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *          eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @param longParamName Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *          eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *         eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  */
        |def foo(x: Int, longParamName: Int): Int
      """.stripMargin

    val after =
      """
        |/**
        |  * Foos the given x, returning foo'ed x.
        |  *
        |  * @param x             Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *                      eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @param longParamName Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *                      eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  *
        |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *         eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  */
        |def foo(x: Int, longParamName: Int): Int
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL3140_removeBlankLines(): Unit = {
    getScalaSettings.SD_BLANK_LINE_BEFORE_TAGS = false

    val before =
      """
        |/**
        |  * Foos the given x, returning foo'ed x.
        |  *
        |  * @param x Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *          eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @param longParamName Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *          eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  *
        |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *         eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  */
        |def foo(x: Int, longParamName: Int): Int
      """.stripMargin

    val after =
      """
        |/**
        |  * Foos the given x, returning foo'ed x.
        |  * @param x             Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *                      eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @param longParamName Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *                      eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *         eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  */
        |def foo(x: Int, longParamName: Int): Int
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL3140_preserveSpacesInTags(): Unit = {
    getScalaSettings.SD_PRESERVE_SPACES_IN_TAGS = true

    val before =
      """
        |/**
        |  * Foos the given x, returning foo'ed x.
        |  *
        |  * @param x             Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *                      eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @param longParamName Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *                      eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  *         eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @throws RuntimeException whenever it feels like it
        |  */
        |def foo(x: Int, longParamName: Int): Int
      """.stripMargin

    doTextTest(before)
  }

  def testSCL8313_1(): Unit = {

    getCommonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    getScalaSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = true
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false

    val before =
      """
        |class Foo(
        |  foo: Int,
        |  bar: Int) {
        |  def baz(
        |    foo2: Int,
        |    bar2: Int) = ???
        |}
      """.stripMargin

    val after =
      """
        |class Foo(
        |    foo: Int,
        |    bar: Int) {
        |  def baz(
        |      foo2: Int,
        |      bar2: Int) = ???
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL8313_2(): Unit = {
    getCommonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    getCommonSettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    getScalaSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = true
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false

    val before =
      """
        |class Foo
        |  (
        |  foo: Int,
        |  bar: Int
        |  ) {
        |  def baz
        |    (
        |    foo2: Int,
        |    bar2: Int
        |    ) = ???
        |}
      """.stripMargin

    val after =
      """
        |class Foo
        |(
        |    foo: Int,
        |    bar: Int
        |) {
        |  def baz
        |  (
        |      foo2: Int,
        |      bar2: Int
        |  ) = ???
        |}
      """.stripMargin

    doTextTest(before, after)
  }


  def testSCL9136_1(): Unit = {
    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE

    val before =
      """
        |package outer {
        |
        |  class OuterClass {
        |    def foo = 42
        |  }
        |
        |  package inner {
        |
        |    class InnerClass {
        |      def bar = 42
        |    }
        |
        |  }
        |
        |}
      """.stripMargin

    val after =
      """
        |package outer
        |{
        |
        |  class OuterClass {
        |    def foo = 42
        |  }
        |
        |  package inner
        |  {
        |
        |    class InnerClass {
        |      def bar = 42
        |    }
        |
        |  }
        |
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL9136_2(): Unit = {
    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2

    val before =
      """
        |package outer {
        |
        |  class OuterClass {
        |    def foo = 42
        |  }
        |
        |  package inner {
        |
        |    class InnerClass {
        |      def bar = 42
        |    }
        |
        |  }
        |
        |}
      """.stripMargin

    val after =
      """
        |package outer
        |  {
        |
        |    class OuterClass {
        |      def foo = 42
        |    }
        |
        |    package inner
        |      {
        |
        |        class InnerClass {
        |          def bar = 42
        |        }
        |
        |      }
        |
        |  }
      """.stripMargin

    doTextTest(before, after)
  }

  def testParameterlessScalaDocTag(): Unit = {
    val before =
      """
        |/**
        |  * @inheritdoc
        |  * @param resource The photo resource.
        |  *                 ara
        |  *                 agara
        |  * @return The saved photo.
        |  */
      """.stripMargin

    doTextTest(before)
  }

  def testDisabledScalaDocTagsNewline(): Unit = {
    getScalaSettings.ENABLE_SCALADOC_FORMATTING = false

    val before =
      """
        |/**
        |  * @param foo is foo
        |  *
        |  * @param bar is bar
        |  */
      """.stripMargin

    doTextTest(before)
  }

  def testScalaDocBlankLineBetweenParameters(): Unit = {
    getScalaSettings.SD_BLANK_LINE_BETWEEN_PARAMETERS = true

    val before =
      """
        |/**
        |  * @param foo is foo
        |  * @param bar is bar
        |  */
      """.stripMargin

    val after =
      """
        |/**
        |  * @param foo is foo
        |  *
        |  * @param bar is bar
        |  */
      """.stripMargin

    doTextTest(before, after)
  }

  def testSpaceInsideClosureBracesDisabled(): Unit = {
    getScalaSettings.SPACE_INSIDE_CLOSURE_BRACES = false

    val before = "def f: Int => String = { x => x.toString }"
    val after = "def f: Int => String = {x => x.toString}"
    doTextTest(before, after)
  }

  def testPatternMatchingAliasSymbolSpacing(): Unit = {
    getScalaSettings.SPACES_AROUND_AT_IN_PATTERNS = true

    val before =
      """"
        |a match {
        |  case c1 :: (rest@(c2 :: cs)) =>
        |}
      """.stripMargin

    val after =
      """"
        |a match {
        |  case c1 :: (rest @ (c2 :: cs)) =>
        |}
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL8939(): Unit = {
    getScalaSettings.ALIGN_TYPES_IN_MULTILINE_DECLARATIONS = true
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false
    getScalaSettings.SPACE_BEFORE_TYPE_COLON = true

    val before =
      """
        |def foo(
        |  aaaaaa: String,
        |  aa: String,
        |  aaaa: String
        |)
      """.stripMargin

    val after =
      """
        |def foo(
        |  aaaaaa : String,
        |  aa     : String,
        |  aaaa   : String
        |)
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL9516(): Unit = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true
    val before =
      """"
        |if (false) { //blah
        |}
      """.stripMargin

    doTextTest(before)
  }

  def testSCL9516_1(): Unit = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true
    val before =
      """"
        |if (false) { //blah
        |  val x = 42
        |}
      """.stripMargin

    doTextTest(before)
  }

  def testSCL6599(): Unit = {
    val before =
      """"
        |/**
        |  * Description
        |  *
        |  * == header ==
        |  *
        |  * - list item 1
        |  *   line 2
        |  *  - list item 1.1
        |  *    line 2
        |  *  - list item 1.2
        |  *    line 2
        |  * 1. 1
        |  * line 2
        |  *  1.1 1.1
        |  *  line 2
        |  * 2. 2
        |  * i. 1
        |  *    line 2
        |  *  i. 1.1
        |  * ii. 2
        |  * A. 1
        |  * B. 2
        |  *   B. 3
        |  *  line 2
        |  * a. 1
        |  *   c. 1.1
        |  * b. 2
        |  */
        |val a = 42
      """.stripMargin

    val after =
      """"
        |/**
        |  * Description
        |  *
        |  * == header ==
        |  *
        |  * - list item 1
        |  * line 2
        |  *  - list item 1.1
        |  * line 2
        |  *  - list item 1.2
        |  * line 2
        |  * 1. 1
        |  * line 2
        |  *  1.1 1.1
        |  * line 2
        |  * 2. 2
        |  * i. 1
        |  * line 2
        |  *  i. 1.1
        |  * ii. 2
        |  * A. 1
        |  * B. 2
        |  *   B. 3
        |  * line 2
        |  * a. 1
        |  *   c. 1.1
        |  * b. 2
        |  */
        |val a = 42
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL10477() = {

    getCommonSettings.KEEP_LINE_BREAKS = false

    val before =
      """
        |class A {
        |  def foo() = {
        |    val logFile = "README.md"
        |    foo()
        |    var foobar = "foobar"
        |    type A = Int
        |    foo()
        |    var foobar1 = "foobar"
        |    type A1 = Int
        |    val logFile1 = "README.md"
        |  }
        |}
      """.stripMargin

    doTextTest(before)
  }

  def testSCL6913() = {
    getScalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = ScalaCodeStyleSettings.NEW_LINE_ALWAYS

    val before =
      """
        |val starCraftGameLogs = StarCraftGameLogs(Map(1 -> "Total destruction by computer player",
        |                                              2 -> "Total destruction again... under 15 minutes",
        |                                              3 -> "Dead before successfully building a single Zergling",
        |                                              4 -> "Man I'm bad at this game!",
        |                                              4 -> "Entire Overlord transport destroyed in transit",
        |                                              4 -> "Glorious victory!!! J/k, 10 guys punched my city into flames.",
        |                                              4 -> "3 on 1 win!"))
      """.stripMargin

    val after =
      """
        |val starCraftGameLogs = StarCraftGameLogs(
        |  Map(
        |    1 -> "Total destruction by computer player",
        |    2 -> "Total destruction again... under 15 minutes",
        |    3 -> "Dead before successfully building a single Zergling",
        |    4 -> "Man I'm bad at this game!",
        |    4 -> "Entire Overlord transport destroyed in transit",
        |    4 -> "Glorious victory!!! J/k, 10 guys punched my city into flames.",
        |    4 -> "3 on 1 win!"))
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL6913_1() = {
    getScalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = ScalaCodeStyleSettings.NEW_LINE_FOR_MULTIPLE_ARGUMENTS

    val before =
      """
        |val starCraftGameLogs = StarCraftGameLogs(Map(1 -> "Total destruction by computer player",
        |                                              2 -> "Total destruction again... under 15 minutes",
        |                                              3 -> "Dead before successfully building a single Zergling",
        |                                              4 -> "Man I'm bad at this game!",
        |                                              4 -> "Entire Overlord transport destroyed in transit",
        |                                              4 -> "Glorious victory!!! J/k, 10 guys punched my city into flames.",
        |                                              4 -> "3 on 1 win!"))
      """.stripMargin

    val after =
      """
        |val starCraftGameLogs = StarCraftGameLogs(Map(
        |  1 -> "Total destruction by computer player",
        |  2 -> "Total destruction again... under 15 minutes",
        |  3 -> "Dead before successfully building a single Zergling",
        |  4 -> "Man I'm bad at this game!",
        |  4 -> "Entire Overlord transport destroyed in transit",
        |  4 -> "Glorious victory!!! J/k, 10 guys punched my city into flames.",
        |  4 -> "3 on 1 win!"))
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL6913_2() = {
    getScalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = ScalaCodeStyleSettings.NEW_LINE_FOR_MULTIPLE_ARGUMENTS
    getCommonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    val before =
      """
        |val fears = List("spiders", "heights", "many spiders", "complex SQL",
        |  "losing at StarCraft", "EMPs")
      """.stripMargin

    val after =
      """
        |val fears = List(
        |  "spiders",
        |  "heights",
        |  "many spiders",
        |  "complex SQL",
        |  "losing at StarCraft",
        |  "EMPs")
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL6267() = {

    val before =
      """
        |import net.liftweb.json.JsonDSL.{symbol2jvalue => _, _} // collision with Matcher's have 'symbol implicit
        |import java.util.UUID
      """.stripMargin

    doTextTest(before)
  }

  def testSCL6267_1() = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false
    val before =
      """
        |import net.liftweb.json.JsonDSL.{symbol2jvalue => _, _} // collision with Matcher's have 'symbol implicit
        |import java.util.UUID
      """.stripMargin

    val after =
      """
        |import net.liftweb.json.JsonDSL.{symbol2jvalue => _, _}
        |// collision with Matcher's have 'symbol implicit
        |import java.util.UUID
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL5032() = {
    val before =
      """
        |collection.map { item =>
        |  item.property
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL4890() = {
    getScalaSettings.ALIGN_IF_ELSE = true
    val before =
      """
        |val recentProgresses = if (guids.nonEmpty) Nil
        |                       else {
        |                         unblob(statuses.flatMap { sum =>
        |                           for {
        |                             prog <- sum.progresses
        |                             if prog.scanTime >= oldest
        |                             if systemGuids.isEmpty || systemGuids.contains(prog.systemGuid)
        |                           } yield prog
        |                         })
        |                       }
      """.stripMargin
    doTextTest(before)
  }

  def testSCL10520() = {
    getCommonSettings.KEEP_LINE_BREAKS = false
    val before = "\"\"\"\n  |foo\n  |bar\n\"\"\""
    doTextTest(before)
  }

  def testSCL8889() = {
    val before =
      """
        |object MyObj {
        |  def :=(t: (String, String)) = ???
        |}
        |
        |MyObj:=("toto", "tata")
        |MyObj:=(("toto", "tata"))
        |MyObj:=Tuple2("toto", "tata")
      """.stripMargin

    val after =
      """
        |object MyObj {
        |  def :=(t: (String, String)) = ???
        |}
        |
        |MyObj := ("toto", "tata")
        |MyObj := (("toto", "tata"))
        |MyObj := Tuple2("toto", "tata")
      """.stripMargin

    doTextTest(before, after)
  }

  def testSCL9990() = {
    getScalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL = false
    val before = "Seq(1, 2, 3).map { case x => x * x }"
    val after = "Seq(1, 2, 3).map{ case x => x * x }"

    doTextTest(before, after)
  }

  def testSCL4291() = {
    getScalaSettings.DO_NOT_INDENT_TUPLES_CLOSE_BRACE = true
    val before =
      """
        |(
        |  a,
        |  b
        |  )
      """.stripMargin
    val after =
      """
        |(
        |  a,
        |  b
        |)
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL4291_1() = {
    getScalaSettings.ALIGN_TUPLE_ELEMENTS = true
    getScalaSettings.DO_NOT_INDENT_TUPLES_CLOSE_BRACE = false
    val before =
      """
        |val foo = (
        |a,
        |b
        |)
      """.stripMargin
    val after =
      """
        |val foo = (
        |            a,
        |            b
        |            )
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL4291_2() = {
    getScalaSettings.ALIGN_TUPLE_ELEMENTS = true
    getScalaSettings.DO_NOT_INDENT_TUPLES_CLOSE_BRACE = true
    val before =
      """
        |val foo = (
        |a,
        |b
        |)
      """.stripMargin
    val after =
      """
        |val foo = (
        |            a,
        |            b
        |          )
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL4743() = {
    val before =
      """
        |def f = if (true) 1 else {
        |  0
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL5025() = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    getScalaSettings.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS = true
    val before =
      """
        |multipleParams(delay = 3,
        |param2 = 4,
        |param3 = 5){
        |println("foo")
        |}
      """.stripMargin
    val after =
      """
        |multipleParams(delay = 3,
        |               param2 = 4,
        |               param3 = 5) {
        |  println("foo")
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL5025_1() = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    getScalaSettings.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS = true
    val before =
      """
        |abstract class Simulation {
        |  def afterDelay(delay: Int)(block: => Unit) {
        |    val item = WorkItem(time = currentTime + delay, action = () => block)
        |    agenda = insert(agenda, item)
        |  }
        |
        |  def run() {
        |    afterDelay(0) {
        |      println("*** simulation started, time = " + currentTime + " ***")
        |    }
        |    while (!agenda.isEmpty) next()
        |  }
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL5585() = {
    getCommonSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    val before =
      """|trait Foo {}
         |
         |class Bar extends Foo
         |  with Foo
         |{}
         |
         |class Baz {}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL5585_1() = {
    getCommonSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    val before =
      """
        |trait Foo {}
        |
        |class Bar extends Foo
        |  with Foo {}
        |
        |class Baz {}
      """.stripMargin

    val after =
      """
        |trait Foo {}
        |
        |class Bar extends Foo
        |  with Foo
        |{}
        |
        |class Baz {}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL6438() = {
    getCommonSettings.BLANK_LINES_BEFORE_IMPORTS = 0
    val before =
      """
        |object O {
        |  import foo.bar
        |
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL6696() = {
    getScalaSettings.DO_NOT_INDENT_TUPLES_CLOSE_BRACE = true
    val before =
      """
        |val a = (52,
        |  52
        |)
      """.stripMargin
    doTextTest(before)
  }

  def testSCL7001() = {
    val before =
      """
        |type Set =
        |  Int => Boolean
      """.stripMargin
    doTextTest(before)
  }

  def testSCL6576() = {
    getScalaSettings.INDENT_FIRST_PARAMETER_CLAUSE = true
    val before =
      """
        |implicit def foo
        |(a: Int)
        |(b: Int) = ???
      """.stripMargin
    val after =
      """
        |implicit def foo
        |  (a: Int)
        |  (b: Int) = ???
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL5032_1() = {

    val before =
      """
        |collection.map { _ => doStuff()
        |item.property}
      """.stripMargin
    val after =
      """
        |collection.map { _ =>
        |  doStuff()
        |  item.property
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL5032_2() = {
    val before = "collection.map { _ => item.property }"
    doTextTest(before)
  }

  def testSCL10527() = {
    val before =
      """
        |def xyz(arg: String): String =
        |  "good formatting"
        |
        |/**
        |  *
        |  * @param arg
        |  * @return
        |  */
        |def xyz1(arg: string): String =
        |  "wrong formatting"
        |
        |val x =
        |  42
        |
        |//someComment
        |val x1 =
        |  42
        |
        |var y =
        |  42
        |
        |/*Other comment*/
        |var y1 =
        |  42
        |
        |//comment
        |type T =
        |  Int
      """.stripMargin
    doTextTest(before)
  }

  def testSCL10527_1() = {
    getCommonSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    val before =
      """
        |//comment
        |class Foo
        |{
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL10527_2() = {
    getCommonSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    val before =
      """
        |//comment
        |class Foo
        |  {
        |  }
      """.stripMargin
    doTextTest(before)
  }

  def testSCL7048() = {
    getCommonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    val before =
      """
        |foo( a, b, c )
        |bar()
      """.stripMargin
    doTextTest(before)
  }

  def testSCL7048_1() = {
    getCommonSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true
    val before =
      """
        |foo(a, b, c)
        |bar( )
      """.stripMargin
    doTextTest(before)
  }

  def testSCL7171() = {
    val before =
      """
        |_ fold(
        |  _ => ???,
        |  _ => ???
        |)
      """.stripMargin
    doTextTest(before)
  }

  def testSCL7453() = {
    getCommonSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    getScalaSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = true
    getCommonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false
    getCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER = 1
    val before =
      """
        |case class ImAClass(something1: Int, something2: Int, something3: Int, something4: Int, something5: Int) {
        |  val uselessVal = 1
        |}
      """.stripMargin
    val after =
      """
        |case class ImAClass(
        |    something1: Int,
        |    something2: Int,
        |    something3: Int,
        |    something4: Int,
        |    something5: Int) {
        |
        |  val uselessVal = 1
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL7690() = {
    getCommonSettings.SPACE_BEFORE_TYPE_PARAMETER_LIST = true
    val before = "bar[A, B]()"
    val after = "bar [A, B]()"
    doTextTest(before, after)
  }

  def testSCL7690_1() = {
    getScalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST = true
    val before = "def bar[A, B]: Int = 42"
    val after = "def bar [A, B]: Int = 42"
    doTextTest(before, after)
  }

  def testSCL9066() = {
    getScalaSettings.TRY_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    val before =
      """
        |try {
        |  42
        |} catch {
        |  case _: Exception => 42
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL9066_1() = {
    getScalaSettings.TRY_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    val before =
      """
        |try 42 catch {
        |  case _: Exception => 42
        |}
      """.stripMargin
    val after =
      """
        |try {
        |  42
        |} catch {
        |  case _: Exception => 42
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL_10545() = {
    getScalaSettings.CASE_CLAUSE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    val before =
      """
        |42 match {
        |  case 42 => {
        |    42
        |  }
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL_10545_1() = {
    getScalaSettings.CASE_CLAUSE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    val before =
      """
        |42 match {
        |  case 42 => 42
        |}
      """.stripMargin
    val after =
      """
        |42 match {
        |  case 42 => {
        |    42
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL9072() = {
    val before = "whenReady(dao.findNetworkRule(\"A12345\")) {          _ => ()          }"
    val after = "whenReady(dao.findNetworkRule(\"A12345\")) { _ => () }"
    doTextTest(before, after)
  }

  def testSCL9321() = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true
    val before =
      """
        |object Test { //before fun call
        |  println(42) //before val
        |  val x = 42 //before var
        |  var y = 42 //before def
        |  def z = 42 //before type
        |  type F = Int //before class
        |  class Inner //before object
        |  object OInner //before trait
        |  trait TInner
        |
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL9321_1(): Unit = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true
    val before =
      """
        |object Test { //before fun call
        |  println(42) //before val
        |
        |  val x = 42 //before var
        |
        |  var y = 42 //before def
        |
        |  def z = 42 //before type
        |
        |  type F = Int //before class
        |
        |  class Inner //before object
        |
        |  object OInner //before trait
        |
        |  trait TInner
        |
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL4269() = {
    val before =
      """
        |object HelloWorld { // A sample application object
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL4269_1() = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = false
    val before =
      """
        |object HelloWorld { // A sample application object
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
      """.stripMargin
    val after =
      """
        |object HelloWorld {
        |  // A sample application object
        |  def main(args: Array[String]) {
        |    println("Hello, world!")
        |  }
        |
        |  def otherFun = 42
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL9450() = {
    getScalaSettings.PLACE_SELF_TYPE_ON_NEW_LINE = false
    getScalaSettings.SPACE_INSIDE_SELF_TYPE_BRACES = true
    val before =
      """
        |trait Something { this: Runnable =>
        |
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL9450_1() = {
    getScalaSettings.PLACE_SELF_TYPE_ON_NEW_LINE = false
    getScalaSettings.SPACE_INSIDE_SELF_TYPE_BRACES = true
    val before =
      """
        |trait Something { this: Runnable => }
      """.stripMargin
    doTextTest(before)
  }

  def testSCL9721() = {
    getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = true
    val before =
      """
        |trait Bar
        |
        |trait Foo extends Bar
        |// with Baz
        |{
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL9786() = {
    getCommonSettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    val before =
      """
        |if (true)
        |  println("1")
        |else
        |  println("2")
      """.stripMargin
    val after =
      """
        |if (true) {
        |  println("1")
        |} else {
        |  println("2")
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL9786_1() = {
    getCommonSettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    getCommonSettings.ELSE_ON_NEW_LINE = true
    val before = "if (true) -1 else 42"
    val after =
      """
        |if (true) {
        |  -1
        |}
        |else {
        |  42
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL9869() = {
    getScalaSettings.SD_KEEP_BLANK_LINES_BETWEEN_TAGS = true
    val before =
      """
        |//
        |// A single line comment 1
        |//
        |// A single line comment 2
        |//
        |
        |/*
        |Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec a diam lectus. Sed sit amet ipsum mauris. Maecenas congue
        |ligula ac quam viverra nec consectetur ante hendrerit. Donec et mollis dolor. Praesent et diam eget libero egestas
        |mattis sit amet vitae augue. Nam tincidunt congue enim, ut porta lorem lacinia consectetur.
        |
        |
        |Donec ut libero sed arcu vehicula ultricies a non tortor. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
        |Aenean ut gravida lorem. Ut turpis felis, pulvinar a semper sed, adipiscing id dolor. Pellentesque auctor nisi id
        |magna consequat sagittis. Curabitur dapibus enim sit amet elit pharetra tincidunt feugiat nisl imperdiet.
        |*/
        |
        |/** Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec a diam lectus. Sed sit amet ipsum mauris. Maecenas
        |  * congue ligula ac quam viverra nec consectetur ante hendrerit. Donec et mollis dolor. Praesent et diam eget libero
        |  * egestas mattis sit amet vitae augue. Nam tincidunt congue enim, ut porta lorem lacinia consectetur.
        |  *
        |  *
        |  * Donec ut libero sed arcu vehicula ultricies a non tortor. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
        |  * Aenean ut gravida lorem. Ut turpis felis, pulvinar a semper sed, adipiscing id dolor. Pellentesque auctor nisi id
        |  * magna consequat sagittis. Curabitur dapibus enim sit amet elit pharetra tincidunt feugiat nisl imperdiet.
        |  *
        |  *
        |  * @constructor does something
        |  *
        |  * @param p1 String. A parameter
        |  *
        |  * @param p2 String. A parameter
        |  *
        |  * @param p3 String. A parameter
        |  *
        |  * @return something
        |  *
        |  * @since 1.0
        |  * @version 1.0
        |  *
        |  * @note a final remark.
        |  */
        |class Demo(p1: String, p2: String, p3: String) {
        |
        |  def aMethod(): Unit = {}
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL10632() = {
    val before =
      """
        |class IndentBug {
        |  //someComment
        |  lazy val myVal =
        |    42
        |
        |  //someComment
        |  private val myVal2 =
        |    42
        |
        |  /*Some other comment*/
        |  override def foo =
        |    42
        |
        |  /**
        |    * ScalaDoc
        |    */
        |  protected def foo2 =
        |    42
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL4280(): Unit = {
    val before =
      """
        |/**
        |  * @see [[com.example.MyClass#myMethod(String)]]
        |  */
        |def myOtherMethod(s: String) = ???
      """.stripMargin
    doTextTest(before)
  }

  def testSCL12297(): Unit = {
    val before =
      """
        |/**
        |  * smth
        |  */
        |@throws(classOf[A])
        |def myMethod(p1: Int, p2: String): A =
        |  foo(p1, this)
      """.stripMargin
    doTextTest(before)
  }

  def testSCL12416(): Unit = {
    val before =
      """
        |// single line comment
        |// another single line comment
        |def foo(x: Int): Int =
        |  if (x > 5) x * 2
        |  else if (x > 3) x
        |  else -x
      """.stripMargin
    doTextTest(before)
  }

  def testSCL12299(): Unit = {
    val before =
      """
        |Nil.foreach { a =>
        |}
        |
        |s"Hello, ${name.toString}"
      """.stripMargin
    doTextTest(before)
  }

  def testSCL12299_1(): Unit = {
    getScalaSettings.SPACE_INSIDE_CLOSURE_BRACES = true
    getScalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = true
    getCommonSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
    getCommonSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    val before =
      """
        |Nil.foreach { a =>
        |}
        |
        |s"Hello, ${name.toString}"
      """.stripMargin
    doTextTest(before)
  }

  def testSCL12353(): Unit = {
    getCommonSettings.BLANK_LINES_AROUND_FIELD = 1

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
    doTextTest(before)
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
    getCommonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 1
    getCommonSettings.BLANK_LINES_AROUND_FIELD = 1
    getCommonSettings.BLANK_LINES_AROUND_METHOD = 1
    getCommonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 1

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
        |
        |}
      """.stripMargin
    doTextTest(SCL12353Before, after)
  }

  def testSCL12353_2(): Unit = {
    getScalaSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = 0
    getScalaSettings.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES = 1
    getCommonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 0
    getCommonSettings.BLANK_LINES_AROUND_FIELD = 0
    getCommonSettings.BLANK_LINES_AROUND_METHOD = 0
    getCommonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 0

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
        |
        |}
      """.stripMargin
    doTextTest(SCL12353Before, after)
  }

  def testSCL12353_3(): Unit = {
    getCommonSettings.BLANK_LINES_AROUND_FIELD = 1
    getCommonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 1
    getCommonSettings.BLANK_LINES_AROUND_METHOD = 0
    getCommonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 0
    getScalaSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = 0
    getScalaSettings.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES = 0
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
        |
        |}
      """.stripMargin
    doTextTest(SCL12353Before, after)
  }

  def testSCL12347(): Unit = {
    val before =
      """
        |foo[(String, Int)](
        |  x,
        |  { case (s, _) ⇒ s },
        |  y,
        |  { case (_, i) ⇒ i }
        |)
      """.stripMargin
    doTextTest(before)
  }

  def testSCL12258(): Unit = {

    val before =
      """
        |
        |object Foo {
        |  for {
        |    // take first
        |    fst <- Some(4)
        |    // take second
        |    snd <- Some(2)
        |  } println(s"${fst}${snd}")
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testSCL4290(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    val before =
      """
        |class SCL4290 {
        |  super.getFoo()
        |    .foo()
        |    .getBar()
        |    .bar()
        |}
      """.stripMargin
    val after =
      """
        |class SCL4290 {
        |  super.getFoo()
        |       .foo()
        |       .getBar()
        |       .bar()
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testSCL4290_1(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    val before =
      """
        |val x = foo().
        |        foo().
        |        goo()
      """.stripMargin
    doTextTest(before)
  }

  def testSCL12066(): Unit = {
    getCommonSettings.WRAP_LONG_LINES = true
    val before = "import com.google.cloud.bigquery.{BigQueryOptions, DatasetId, FormatOptions, Job, JobInfo, LoadJobConfiguration, StandardTableDefinition, TableId, TableInfo, ViewDefinition}"
    val after =
      """
        |import com.google.cloud.bigquery.{BigQueryOptions, DatasetId, FormatOptions, Job, JobInfo, LoadJobConfiguration,
        |  StandardTableDefinition, TableId, TableInfo, ViewDefinition}
      """.stripMargin
    doTextTest(before, after)
    //TODO formatting engine is not able to first wrap, and then calculate spacings in a single pass
    val after2 =
      """
        |import com.google.cloud.bigquery.{
        |  BigQueryOptions, DatasetId, FormatOptions, Job, JobInfo, LoadJobConfiguration,
        |  StandardTableDefinition, TableId, TableInfo, ViewDefinition
        |}
      """.stripMargin
    doTextTest(after, after2)
  }

  private val beforeSCL3536 =
    """
      |class A extends B
      |with C
    """.stripMargin
  def testSCL3536(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_EXTENDS_LIST = true
    getScalaSettings.ALIGN_EXTENDS_WITH = ScalaCodeStyleSettings.ON_FIRST_TOKEN
    val after =
      """
        |class A extends B
        |                with C
      """.stripMargin
    doTextTest(beforeSCL3536, after)
  }
// TODO temporarily disabled untile formatter engine has a way to produce desired alignment
//  def testSCL3536_1(): Unit = {
//    getScalaSettings.ALIGN_EXTENDS_WITH = ScalaCodeStyleSettings.ON_FIRST_ANCESTOR
//    val after =
//      """
//        |class A extends B
//        |           with C
//      """.stripMargin
//
//    doTextTest(beforeSCL3536, after)
//  }
  def testSCL3536_2(): Unit = {
    getScalaSettings.ALIGN_EXTENDS_WITH = ScalaCodeStyleSettings.ALIGN_TO_EXTENDS
    val after =
      """
        |class A extends B
        |        with C
      """.stripMargin
    doTextTest(beforeSCL3536, after)
  }
  def testSCL3536_3(): Unit = {
    getScalaSettings.ALIGN_EXTENDS_WITH = ScalaCodeStyleSettings.ON_FIRST_TOKEN
    val before =
      """
        |class A extends B with
        |C
      """.stripMargin
    val after =
      """
        |class A extends B with
        |                C
      """.stripMargin
    doTextTest(before, after)
  }


  def doTextTest(value: String): Unit = doTextTest(value, value)
}