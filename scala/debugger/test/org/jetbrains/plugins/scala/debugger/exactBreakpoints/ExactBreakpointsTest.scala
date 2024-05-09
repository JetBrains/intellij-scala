package org.jetbrains.plugins.scala
package debugger
package exactBreakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.DocumentUtil
import com.intellij.xdebugger.XDebuggerUtil
import org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.junit.Assert
import org.junit.Assert.{assertTrue, fail}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

class ExactBreakpointsTest_2_11 extends ExactBreakpointsTestWithEarlyDefinitions {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class ExactBreakpointsTest_2_12 extends ExactBreakpointsTestWithEarlyDefinitions {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  addSourceFile("SamAbstractClass.scala",
    s"""object SamAbstractClass {
       |
       |  def main(args: Array[String]): Unit = {
       |    val test: Parser[String] = (in: String) => {
       |      println() $breakpoint
       |      in
       |    }
       |
       |    test.parse(string) $breakpoint
       |
       |    parse(string)(firstChar) $breakpoint ${lambdaOrdinal(0)}
       |  }
       |
       |  def parse[T](s: String)(p: Parser[T]) = p.parse(s)
       |
       |  def firstChar(s: String): Option[Char] = s.headOption $breakpoint
       |
       |  val string = "string"
       |
       |  abstract class Parser[T] {
       |    def parse(s: String): T
       |  }
       |}
    """.stripMargin)

  def testSamAbstractClass(): Unit = {
    exactBreakpointTest()(
      "test.parse(string)",
      "println()",
      "firstChar",
      "def firstChar..."
    )
  }
}

class ExactBreakpointsTest_2_13 extends ExactBreakpointsTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ExactBreakpointsTest_3 extends ExactBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  addSourceFile("TopLevelDefinitions.scala",
    s"""object a:
       |  def two() = 2 $breakpoint
       |
       |  object b:
       |    def three =
       |      3 $breakpoint
       |end a
       |
       |import a.two
       |import a.b.three
       |
       |def one() = 1 $breakpoint
       |
       |@main
       |def topLevelMain(): Unit =
       |  println(0) $breakpoint
       |  println(one())
       |  println(two())
       |  println(three)
      """.stripMargin.trim
  )

  def testTopLevelDefinitions(): Unit = {
    exactBreakpointTest("topLevelMain")("println(0)", "def one() = 1", "def two() = 2", "def two() = 2", "3")
  }

  addSourceFile("MainAnnotation.scala",
    s"""object MainAnnotation:
       |  object Inner:
       |    @main
       |    def mainInInnerObject(): Unit =
       |      println(42) $breakpoint
      """.stripMargin.trim
  )

  def testMainInInnerObject(): Unit = {
    exactBreakpointTest("mainInInnerObject")("println(42)")
  }

  addSourceFile("SCL21348.scala",
    s"""@main def helloMe() = {
       |  println("this is way cooler")
       |  val m = Map("lau" -> "yeser",
       |    "data" -> "base")
       |
       |  for ((k, v) <- m) {
       |    val k1 = "hello" $breakpoint
       |    val k = k1.length + 320 $breakpoint
       |    println(s"$${k} - $${v}") $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testSCL21348(): Unit = {
    exactBreakpointTest("helloMe")(
    """val k1 = "hello"""", "val k = k1.length + 320", """println(s"${k} - ${v}")""",
    """val k1 = "hello"""", "val k = k1.length + 320", """println(s"${k} - ${v}")"""
    )
  }

  addSourceFile("mypackage/SCL21348.scala",
    s"""package mypackage
       |
       |@main def helloMe() = {
       |  println("this is way cooler")
       |  val m = Map("lau" -> "yeser",
       |    "data" -> "base")
       |
       |  for ((k, v) <- m) {
       |    val k1 = "hello" $breakpoint
       |    val k = k1.length + 320 $breakpoint
       |    println(s"$${k} - $${v}") $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testSCL21348_withPackage(): Unit = {
    exactBreakpointTest("mypackage.helloMe")(
      """val k1 = "hello"""", "val k = k1.length + 320", """println(s"${k} - ${v}")""",
      """val k1 = "hello"""", "val k = k1.length + 320", """println(s"${k} - ${v}")"""
    )
  }

  override def testConstructorAndClassParam(): Unit = {}

  override def testNestedLambdas(): Unit = {}
}

class ExactBreakpointsTest_3_RC extends ExactBreakpointsTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class ExactBreakpointsTestWithEarlyDefinitions extends ExactBreakpointsTestBase {
  addSourceFile("EarlyDefAndTemplateBody.scala",
    s"""object EarlyDefAndTemplateBody {
       |  def main(args: Array[String]): Unit = {
       |    new CCC()
       |  }
       |}
       |
       |class CCC extends {
       |  val x = None.getOrElse(Seq(1)).filter(_ > 0) $breakpoint
       |} with DDD("") {
       |  Seq(1).map(x => x + 1).filter(_ > 10) $breakpoint
       |}
       |
       |class DDD(s: String)""".stripMargin.trim
  )

  def testEarlyDefAndTemplateBody(): Unit = {
    checkVariants()(7, "Line and Lambdas", "early definitions of CCC", "Seq(1)", "_ > 0")
    checkVariants()(9, "Line and Lambdas", "line in containing block", "x => x + 1", "_ > 10")

    exactBreakpointTest()("val x = ...", "Seq(1)", "_ > 0", "Seq(1).map...", "x => x + 1", "_ > 10")
  }
}

abstract class ExactBreakpointsTestBase extends ScalaDebuggerTestCase {

  private val expectedSourcePositionsQueue: ConcurrentLinkedQueue[String] =
    new ConcurrentLinkedQueue()

  protected def exactBreakpointTest(mainClass: String = getTestName(false))
                                   (sourcePositions: String*): Unit = {
    assertTrue("The debugger should stop on at least 1 breakpoint", sourcePositions.nonEmpty)
    expectedSourcePositionsQueue.addAll(sourcePositions.asJava)

    createLocalProcess(mainClass)

    val debugProcess = getDebugProcess
    val positionManager = ScalaPositionManager.instance(debugProcess).getOrElse(new ScalaPositionManager(debugProcess))

    onEveryBreakpoint { ctx =>
      val loc = ctx.getFrameProxy.getStackFrame.location()
      inReadAction {
        val srcPos = positionManager.getSourcePosition(loc)
        val actual = highlightedText(srcPos)
        Option(expectedSourcePositionsQueue.poll()) match {
          case None =>
            fail(s"The debugger stopped on line ${srcPos.getLine}, but there were no more expected lines")
          case Some(expected) =>
            if (!actual.startsWith(expected.stripSuffix("..."))) {
              fail(s"Wrong source position. Expected: $expected, actual: $actual")
            }
        }
      }
      resume(ctx)
    }
  }

  protected def checkVariants(className: String = getTestName(false))(lineNumber: Int, variants: String*): Unit =
    inReadAction {
      val manager = ScalaPsiManager.instance(getProject)
      val psiClass = manager.getCachedClass(GlobalSearchScope.allScope(getProject), className)
      val psiFile = psiClass.map(_.getContainingFile).getOrElse(throw new AssertionError(s"Could not find class $className"))
      val virtualFile = psiFile.getVirtualFile
      val scalaBreakpointType = XDebuggerUtil.getInstance().findBreakpointType(classOf[ScalaLineBreakpointType])
      val xSourcePosition = XDebuggerUtil.getInstance().createPosition(virtualFile, lineNumber)
      val foundVariants = scalaBreakpointType.computeVariants(getProject, xSourcePosition).asScala.map(_.getText)
      Assert.assertEquals("Wrong set of variants found: ", variants, foundVariants)
    }

  private def highlightedText(position: SourcePosition): String = {
    val elemRange = SourcePositionHighlighter.getHighlightRangeFor(position)
    val document = PsiDocumentManager.getInstance(getProject).getDocument(position.getFile)
    val lineRange = DocumentUtil.getLineTextRange(document, position.getLine)
    val textRange = if (elemRange ne null) elemRange else lineRange
    document.getText(textRange).trim
  }

  addSourceFile("OneLine.scala",
    s"""object OneLine {
       |  def main(args: Array[String]): Unit = {
       |    Seq(1).map(x => x + 1).filter(_ > 10).foreach(println) $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testOneLine(): Unit = {
    checkVariants()(2, "Line and Lambdas", "line in function main", "x => x + 1", "_ > 10", "println")

    exactBreakpointTest()("Seq(1).map(...", "x => x + 1", "_ > 10")
  }

  addSourceFile("Either.scala",
    s"""object Either {
       |  def main(args: Array[String]): Unit = {
       |    val x: Either[String, Int] = Right(1)
       |    val y: Either[String, Int] = Left("aaa")
       |
       |    x.fold(_.substring(1), _ + 1) $breakpoint
       |    y.fold(_.substring(2), _ + 2) $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testEither(): Unit = {
    checkVariants()(5, "Line and Lambdas", "line in function main", "_.substring(1)", "_ + 1")
    exactBreakpointTest()("x.fold(...", "_ + 1", "y.fold(...", "_.substring(2)")
  }

  addSourceFile("SeveralLines1.scala",
    s"""object SeveralLines1 {
       |  def main(args: Array[String]): Unit = {
       |    Option("aaa").flatMap(_.headOption) $breakpoint
       |      .find(c => c.isDigit).getOrElse('0') $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testSeveralLines1(): Unit = {
    checkVariants()(2, "Line and Lambda", "line in function main", "_.headOption")
    checkVariants()(3, "Line and Lambdas", "line in function main", "c => c.isDigit", "'0'")

    exactBreakpointTest()("Option(\"aaa\")...", "_.headOption", ".find(...", "c => c.isDigit", "'0'")
  }

  addSourceFile("SeveralLines2.scala",
    s"""object SeveralLines2 {
       |  def main(args: Array[String]): Unit = {
       |    Option("aaa").flatMap(_.headOption) $breakpoint ${lambdaOrdinal(-1)}
       |      .find(c => c.isDigit).getOrElse('0') $breakpoint ${lambdaOrdinal(-1)}
       |  }
       |}""".stripMargin.trim
  )

  def testSeveralLines2(): Unit = {
    checkVariants()(2, "Line and Lambda", "line in function main", "_.headOption")
    checkVariants()(3, "Line and Lambdas", "line in function main", "c => c.isDigit", "'0'")

    exactBreakpointTest()("Option(...", ".find(...")
  }

  addSourceFile("SeveralLines3.scala",
    s"""object SeveralLines3 {
       |  def main(args: Array[String]): Unit = {
       |    Option("aaa").flatMap(_.headOption) $breakpoint ${lambdaOrdinal(0)}
       |      .find(c => c.isDigit).getOrElse('0') $breakpoint ${lambdaOrdinal(0)}
       |  }
       |}""".stripMargin.trim
  )

  def testSeveralLines3(): Unit = {
    checkVariants()(2, "Line and Lambda", "line in function main", "_.headOption")
    checkVariants()(3, "Line and Lambdas", "line in function main", "c => c.isDigit", "'0'")

    exactBreakpointTest()("_.headOption", "c => c.isDigit")
  }

  addSourceFile("NestedLambdas.scala",
    s"""object NestedLambdas {
       |  def main(args: Array[String]): Unit = {
       |    Seq("a").flatMap(x => x.find(_ == 'a').getOrElse('a').toString).foreach(c => println(Some(c).filter(_ == 'a').getOrElse('b'))) $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testNestedLambdas(): Unit = {
    checkVariants()(2,
      "Line and Lambdas",
      "line in function main",
      "x => x.find(_ == 'a').getOrElse('a').toString",
      "_ == 'a'",
      "'a'",
      "c => println(Some(c).filter(_ == 'a').getOrElse('b'))",
      "_ == 'a'",
      "'b'")

    exactBreakpointTest()("Seq(\"a\").flatMap(...", "x => x.find(...", "_ == 'a'", "c => println...", "_ == 'a'")
  }

  addSourceFile("NestedLambdas2.scala",
    """object NestedLambdas2 {
      |  def main(args: Array[String]): Unit = {
      |    Seq("b").flatMap(x => x.find(_ == 'a').getOrElse('a').toString).foreach(c => println(Some(c).filter(_ == 'b').getOrElse('a')))
      |  }
      |}""".stripMargin.trim
  )

  def testNestedLambdas2(): Unit = {
    checkVariants()(2,
      "Line and Lambdas",
      "line in function main",
      "x => x.find(_ == 'a').getOrElse('a').toString",
      "_ == 'a'",
      "'a'",
      "c => println(Some(c).filter(_ == 'b').getOrElse('a'))",
      "_ == 'b'",
      "'a'")

    exactBreakpointTest()("Seq(\"b\").flatMap(...", "x => x.find(...", "_ == 'a'", "'a'", "c => println...", "_ == 'b'", "'a'")
  }

  addSourceFile("ConstructorAndClassParam.scala",
    s"""object ConstructorAndClassParam {
       |  def main(args: Array[String]): Unit = {
       |    new BBB()
       |  }
       |}
       |
       |class BBB extends AAA("a3".filter(_.isDigit)) { $breakpoint
       |  Seq(1).map(x => x + 1).filter(_ > 10)
       |}
       |
       |class AAA(s: String) $breakpoint""".stripMargin.trim
  )

  def testConstructorAndClassParam(): Unit = {
    checkVariants()(6, "Line and Lambda", "constructor of BBB", "_.isDigit")
    exactBreakpointTest()("class BBB ...", "_.isDigit", "_.isDigit", "class AAA(...")
  }

  addSourceFile("NewTemplateDefinitionAsLambda.scala",
    s"""object NewTemplateDefinitionAsLambda {
       |  def main(args: Array[String]): Unit = {
       |    Seq("a").map(new ZZZ(_)).filter(_ => false).headOption.getOrElse(new ZZZ("1")) $breakpoint
       |  }
       |}
       |
       |class ZZZ(s: String)""".stripMargin.trim
  )

  def testNewTemplateDefinitionAsLambda(): Unit = {
    checkVariants()(2, "Line and Lambdas", "line in function main", "new ZZZ(_)", "_ => false", "new ZZZ(\"1\")")
    exactBreakpointTest()("Seq(\"a\")...", "new ZZZ(_)", "_ => false", "new ZZZ(\"1\")")
  }

  addSourceFile("LineStartsWithDot.scala",
    s"""object LineStartsWithDot {
       |  def main(args: Array[String]): Unit = {
       |    Some(1) $breakpoint
       |      .map(_ + 1) $breakpoint ${lambdaOrdinal(-1)}
       |      .filter(i => i % 2 == 0) $breakpoint ${lambdaOrdinal(0)}
       |      .foreach(println) $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testLineStartsWithDot(): Unit = {
    checkVariants()(2) //no variants
    checkVariants()(3, "Line and Lambda", "line in function main", "_ + 1")
    checkVariants()(4, "Line and Lambda", "line in function main", "i => i % 2 == 0")
    checkVariants()(5, "Line and Lambda", "line in function main", "println")

    exactBreakpointTest()("Some(1)", ".map...", "i => i % 2 == 0", ".foreach...", "println")
  }

  addSourceFile("WholeLineIsLambda.scala",
    s"""object WholeLineIsLambda {
       |  def main(args: Array[String]): Unit = {
       |    Seq(1, 2, 3).foreach {
       |      Seq(4)
       |    }
       |    Seq(1, 2, 3).foreach {
       |      Seq(4).map(_ + 1)
       |    }
       |    Seq(1, 2, 3).foreach {
       |      Seq(4).map(_ + 1).map(_ + 2)
       |    }
       |  }
       |}""".stripMargin.trim
  )

  def testWholeLineIsLambda(): Unit = {
    checkVariants()(3) //no variants
    checkVariants()(6, "Line and Lambda", "line in containing block", "_ + 1")
    checkVariants()(9, "Line and Lambdas", "line in containing block", "_ + 1", "_ + 2")
  }

  addSourceFile("PartialFunctionArg.scala",
    s"""object PartialFunctionArg {
       |  def main(args: Array[String]): Unit = {
       |    Seq(Option(1)).foreach {
       |      case None =>
       |        println(true)
       |      case Some(i) => $breakpoint
       |        println(false) $breakpoint
       |    }
       |  }
       |}
    """.stripMargin.trim)

  def testPartialFunctionArg(): Unit = {
    exactBreakpointTest()("case Some(i) =>", "println(false)")
  }

  addSourceFile("LikeDefaultArgName.scala",
    s"""object LikeDefaultArgName {
       |  def main(args: Array[String]): Unit = {
       |    def default() = {
       |      println("stop here") $breakpoint
       |    }
       |
       |    None.getOrElse(default())
       |  }
       |}""".stripMargin)

  def testLikeDefaultArgName(): Unit = {
    exactBreakpointTest()("""println("stop here")""")
  }

  addSourceFile("BreakpointInTrait.scala",
    s"""object BreakpointInTrait {
       |  def main(args: Array[String]): Unit = {
       |    val a = new AAA
       |    a.foo()
       |    a.foo("x")
       |  }
       |
       |  class AAA extends TraitExample
       |}
       |
       |trait TraitExample extends SecondTrait {
       |  val x = 1 $breakpoint
       |
       |  def foo(): Unit = {
       |    println("2") $breakpoint
       |  }
       |}
       |
       |trait SecondTrait {
       |  def foo(s: String): Unit = {
       |    println("3") $breakpoint
       |  }
       |}
  """.stripMargin)

  def testBreakpointInTrait(): Unit = {
    exactBreakpointTest()("val x = 1", """println("2")""", """println("3")""")
  }

  addSourceFile("BreakpointWithBackticks.scala",
    s"""
       |class `class with backticks` {
       |  def foo(): Unit = {
       |    println(1) $breakpoint
       |  }
       |
       |  def `method with backticks`(): Unit = {
       |    println(2) $breakpoint
       |  }
       |}
       |
       |object BreakpointWithBackticks {
       |  def main(args: Array[String]): Unit = {
       |    new `class with backticks`().foo()
       |    new `class with backticks`().`method with backticks`()
       |  }
       |}""".stripMargin)

  def testBreakpointWithBackticks(): Unit = {
    exactBreakpointTest()("println(1)", "println(2)")
  }

  addSourceFile("MultilineLambda.scala",
    s"""object MultilineLambda {
       |  def main(args: Array[String]): Unit = {
       |    List(1, 2, 3).foreach { x =>
       |      println(x) $breakpoint
       |      println(123) $breakpoint
       |      println()
       |    }
       |  }
       |}""".stripMargin)

  def testMultilineLambda(): Unit = {
    exactBreakpointTest()("println(x)", "println(123)", "println(x)",  "println(123)", "println(x)", "println(123)")
  }
}
