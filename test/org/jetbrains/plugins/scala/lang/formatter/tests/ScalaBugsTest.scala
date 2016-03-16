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

  def doTextTest(value: String): Unit = doTextTest(value, value)
}