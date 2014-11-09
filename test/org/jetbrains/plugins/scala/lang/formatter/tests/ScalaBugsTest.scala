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

  def testSCL2424 {
    val before =
"""
someMethod(new Something, abc, def)
""".replace("\r", "")
    val after =
"""
someMethod(new Something, abc, def)
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL2425 {
    val before =
"""
import foo.{Foo, Bar}
""".replace("\r", "")
    val after =
"""
import foo.{Foo, Bar}
""".replace("\r", "")
    doTextTest(before, after)
  }

  def testSCL2477 {
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

  def testSCL1875 {
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

  def testSCL2066FromDiscussion {
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
    getScalaSettings.INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD = true
    getSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true

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
    getSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false

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
    getSettings.SPACE_BEFORE_METHOD_PARENTHESES = true

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
    getSettings.SPACE_BEFORE_METHOD_PARENTHESES = true

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
    getSettings.SPACE_BEFORE_METHOD_PARENTHESES = false

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
    getSettings.SPACE_BEFORE_METHOD_PARENTHESES = false

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
    getSettings.SPACE_BEFORE_METHOD_PARENTHESES = false

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
    getSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    getSettings.SPACE_BEFORE_METHOD_PARENTHESES = true

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
    getSettings.SPACE_BEFORE_METHOD_PARENTHESES = false
    getSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = false

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
    getSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
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
    getSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
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
}