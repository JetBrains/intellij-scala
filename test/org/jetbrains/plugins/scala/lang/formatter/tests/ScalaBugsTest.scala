package org.jetbrains.plugins.scala.lang.formatter.tests

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaBugsTest extends AbstractScalaFormatterTestBase {
  /* stub:
  def test {
    val before =
"""
""".replace("\r", "")
    val after =
"""
""".replace("\r", "")
    doTextTest(before, after)
  }
   */

  def testSCL2424() {
    val before =
"""
someMethod(new Something, abc, def)
""".replace("\r", "")

    doTextTest(before)
  }

  def testSCL2425() {
    val before =
"""
import foo.{Foo, Bar}
""".replace("\r", "")

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
""".replace("\r", "")
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
""".replace("\r", "")
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
""".replace("\r", "")
    val after =
"""
/**
  * something{@link Foo}
  * something
  */
class A
""".replace("\r", "")
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
""".replace("\r", "")
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
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL2775sTrue() {
    getScalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = true

    val before =
"""
Set(1, 2, 3).filter{a => a % 2 == 0}
List((1, 2), (2, 3), (3, 4)).map {case (k: Int, n: Int) => k + n}
Map(1 -> "aa", 2 -> "bb", 3 -> "cc").filter{ case (1, "aa") => true; case _ => false}
""".replace("\r", "")
    val after =
"""
Set(1, 2, 3).filter { a => a % 2 == 0 }
List((1, 2), (2, 3), (3, 4)).map { case (k: Int, n: Int) => k + n }
Map(1 -> "aa", 2 -> "bb", 3 -> "cc").filter { case (1, "aa") => true; case _ => false }
""".replace("\r", "")

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
""".replace("\r", "")

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
""".replace("\r", "")

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
""".replace("\r", "")

    val after =
"""
def func() { println("test") }

def func2() {
  println("test")
}
""".replace("\r", "")

    doTextTest(before, after)
  }

  def testSCL2839sFalse() {
    getCommonSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false

    val before =
"""
def func() {  println()}

def func2() { println()
}
""".replace("\r", "")

    val after =
"""
def func() {
  println()
}

def func2() {
  println()
}
""".replace("\r", "")

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
""".replace("\r", "")

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
""".replace("\r", "")

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
""".replace("\r", "")

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
""".replace("\r", "")

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
      """.replace("\r", "")

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
""".replace("\r", "")

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
      """.replace("\r", "")

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
""".replace("\r", "")

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
      """.replace("\r", "")

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
""".replace("\r", "")

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
      """.replace("\r", "")

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
""".replace("\r", "")

    doTextTest(before, after)
  }

  def testSCL2474() {
    getCommonSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    getCommonSettings.SPACE_BEFORE_METHOD_PARENTHESES = true

    val before =
"""
def f(i: Int)(j: Int) {}

f(1)(2)
""".replace("\r", "")

    val after =
"""
def f (i: Int)(j: Int) {}

f (1)(2)
""".replace("\r", "")

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
""".replace("\r", "")

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
""".replace("\r", "")

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

      """.replace("\r", "")

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

      """.replace("\r", "")

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

      """.replace("\r", "")

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
      """.replace("\r", "")

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
    """.stripMargin.replace("\r", "")

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
    """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map {t => t.toSeq sortBy {-_} map {_ * 2}}
        |  val f4: (Int, Int) => Int = {_ + _}
        |  val f5: (Int, Int) => Int = {_ + _}
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map { t => t.toSeq sortBy { -_ } map { _ * 2 } }
        |  val f4: (Int, Int) => Int = { _ + _ }
        |  val f5: (Int, Int) => Int = { _ + _ }
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map { t => t.toSeq sortBy {-_} map {_ * 2} }
        |  val f4: (Int, Int) => Int = {_ + _}
        |  val f5: (Int, Int) => Int = {_ + _}
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class SCL5488 {
        |  val foos = List[List[Integer]]()
        |  foos map { t => t.toSeq sortBy { -_ } map { _ * 2 } }
        |  val f4: (Int, Int) => Int = { _ + _ }
        |  val f5: (Int, Int) => Int = { _ + _ }
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |/**
        | * Some comments
        | */
        |class A
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class X {
        |  (for {
        |    i <- 1 to 10
        |  } yield {
        |    1
        |  }).map(_ + 1)
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |val x = for {
        |//Comment
        |  x <- Nil
        |} yield {
        |  x
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |try
        |{
        |  expr
        |} catch
        |{
        |  case _: Throwable => println("gotcha!")
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    doTextTest(before)
  }

  def testSCL2454(): Unit = {
    getCommonSettings.KEEP_LINE_BREAKS = false

    val before =
      """
        |val v
        |    =
        |    "smth"
      """.stripMargin.replace("\r", "")

    val after =
      """
        |val v = "smth"
      """.stripMargin.replace("\r", "")

    doTextTest(before, after)
  }

  def testSCL2468(): Unit = {
    getScalaSettings.NEWLINE_AFTER_ANNOTATIONS = true

    val before =
      """
        |@throws(classOf[IOException]) @deprecated def doSmth() {}
      """.stripMargin.replace("\r", "")

    val after =
      """
        |@throws(classOf[IOException])
        |@deprecated
        |def doSmth() {}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    @deprecated
        |    @deprecated
        |    val myLocalVal = 42
        |  }
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class Foo extends Object with
        |  Thread with
        |  Serializable {
        |  def foo(x: Int = 0,
        |          y: Int = 1,
        |          z: Int = 2) = new Foo
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class Foo
        |  extends Object with Thread {
        |  def foo(x: Int = 0,
        |          y: Int = 1,
        |          z: Int = 2) = new Foo
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class MyLongClassName(someParam: String, someOtherParam: Int) extends SomeClass
        |    with SomeTrait
        |    with AnotherTrait
        |    with AndAnotherTrait
        |    with YetAnotherTrait {
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |/**
        |  * Foos the given x, returning foo'ed x.
        |  *
        |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
        |  * eiusmod tempor incididunt ut labore et dolore magna aliqua.
        |  * @throws RuntimeException whenever it feels like it
        |  */
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |class Foo(
        |    foo: Int,
        |    bar: Int) {
        |  def baz(
        |      foo2: Int,
        |      bar2: Int) = ???
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |/**
        |  * @param foo is foo
        |  *
        |  * @param bar is bar
        |  */
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """"
        |a match {
        |  case c1 :: (rest @ (c2 :: cs)) =>
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |def foo(
        |  aaaaaa : String,
        |  aa     : String,
        |  aaaa   : String
        |)
      """.stripMargin.replace("\r", "")

    doTextTest(before, after)
  }

  def testSCL9516(): Unit = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true
    val before =
      """"
        |if (false) { //blah
        |}
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")

    doTextTest(before)
  }

  def testSCL6267() = {
    getScalaSettings.KEEP_COMMENTS_ON_SAME_LINE = true

    val before =
      """
        |import net.liftweb.json.JsonDSL.{symbol2jvalue => _, _} // collision with Matcher's have 'symbol implicit
        |import java.util.UUID
      """.stripMargin.replace("\r", "")

    doTextTest(before)
  }

  def testSCL6267_1() = {
    val before =
      """
        |import net.liftweb.json.JsonDSL.{symbol2jvalue => _, _} // collision with Matcher's have 'symbol implicit
        |import java.util.UUID
      """.stripMargin.replace("\r", "")

    val after =
      """
        |import net.liftweb.json.JsonDSL.{symbol2jvalue => _, _}
        |// collision with Matcher's have 'symbol implicit
        |import java.util.UUID
      """.stripMargin.replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL5032() = {
    val before =
      """
        |collection.map { item =>
        |  item.property
        |}
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |object MyObj {
        |  def :=(t: (String, String)) = ???
        |}
        |
        |MyObj := ("toto", "tata")
        |MyObj := (("toto", "tata"))
        |MyObj := Tuple2("toto", "tata")
      """.stripMargin.replace("\r", "")

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
      """.stripMargin.replace("\r", "")
    val after =
      """
        |(
        |  a,
        |  b
        |)
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    val after =
      """
        |val foo = (
        |            a,
        |            b
        |            )
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    val after =
      """
        |val foo = (
        |            a,
        |            b
        |          )
      """.stripMargin.replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL4743() = {
    val before =
      """
        |def f = if (true) 1 else {
        |  0
        |}
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    val after =
      """
        |multipleParams(delay = 3,
        |               param2 = 4,
        |               param3 = 5) {
        |  println("foo")
        |}
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")

    val after =
      """
        |trait Foo {}
        |
        |class Bar extends Foo
        |  with Foo
        |{}
        |
        |class Baz {}
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL6696() = {
    getScalaSettings.DO_NOT_INDENT_TUPLES_CLOSE_BRACE = true
    val before =
      """
        |val a = (52,
        |  52
        |)
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL7001() = {
    val before =
      """
        |type Set =
        |  Int => Boolean
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL6576() = {
    getScalaSettings.INDENT_FIRST_PARAMETER_CLAUSE = true
    val before =
      """
        |implicit def foo
        |(a: Int)
        |(b: Int) = ???
      """.stripMargin.replace("\r", "")
    val after =
      """
        |implicit def foo
        |  (a: Int)
        |  (b: Int) = ???
      """.stripMargin.replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL5032_1() = {

    val before =
      """
        |collection.map { _ => doStuff()
        |item.property}
      """.stripMargin.replace("\r", "")
    val after =
      """
        |collection.map { _ =>
        |  doStuff()
        |  item.property
        |}
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL7048() = {
    getCommonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    val before =
      """
        |foo( a, b, c )
        |bar()
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL7048_1() = {
    getCommonSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true
    val before =
      """
        |foo(a, b, c)
        |bar( )
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL7171() = {
    val before =
      """
        |_ fold(
        |  _ => ???,
        |  _ => ???
        |)
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL9066_1() = {
    getScalaSettings.TRY_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    val before =
      """
        |try 42 catch {
        |  case _: Exception => 42
        |}
      """.stripMargin.replace("\r", "")
    val after =
      """
        |try {
        |  42
        |} catch {
        |  case _: Exception => 42
        |}
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL_10545_1() = {
    getScalaSettings.CASE_CLAUSE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    val before =
      """
        |42 match {
        |  case 42 => 42
        |}
      """.stripMargin.replace("\r", "")
    val after =
      """
        |42 match {
        |  case 42 => {
        |    42
        |  }
        |}
      """.stripMargin.replace("\r", "")
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
        |object Test {
        |  println(42) //before val
        |  val x = 42 //before var
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
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL9450() = {
    getScalaSettings.PLACE_SELF_TYPE_ON_NEW_LINE = false
    getScalaSettings.SPACE_INSIDE_SELF_TYPE_BRACES = true
    val before =
      """
        |trait Something { this: Runnable =>
        |
        |}
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def testSCL9450_1() = {
    getScalaSettings.PLACE_SELF_TYPE_ON_NEW_LINE = false
    getScalaSettings.SPACE_INSIDE_SELF_TYPE_BRACES = true
    val before =
      """
        |trait Something { this: Runnable => }
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    val after =
      """
        |if (true) {
        |  println("1")
        |} else {
        |  println("2")
        |}
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
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
      """.stripMargin.replace("\r", "")
    doTextTest(before)
  }

  def doTextTest(value: String): Unit = doTextTest(value, value)
}