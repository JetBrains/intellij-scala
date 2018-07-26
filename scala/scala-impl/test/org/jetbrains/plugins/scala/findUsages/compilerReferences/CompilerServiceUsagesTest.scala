package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.psi.PsiElement
import junit.framework.TestCase.fail
import org.jetbrains.plugins.scala.util.ImplicitUtil.ImplicitSearchTarget
import org.junit.Assert._

class CompilerServiceUsagesTest extends ScalaCompilerReferenceServiceFixture {
  import com.intellij.testFramework.fixtures.CodeInsightTestFixture.{CARET_MARKER => CARET}

  private def implicitSearchTargetAtCaret: PsiElement =
    myFixture.getElementAtCaret match {
      case ImplicitSearchTarget(t) => t
      case e                       => fail(s"Search target must be an implicit definition, but was $e"); ???
    }

  private def runSearchTest(
    files:    (String, String)*
  )(expected: (String, Seq[Int])*)(target: => PsiElement = implicitSearchTargetAtCaret): Unit = {
    val (targetFile, rest) = (files.head, files.tail)

    val filesMap = rest.map {
      case (name, code) =>
        val psiFile = myFixture.addFileToProject(name, code)
        name -> psiFile.getVirtualFile
    }.toMap + {
      val file = myFixture.configureByText(targetFile._1, targetFile._2)
      targetFile._1 -> file.getVirtualFile
    }

//    BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
//    com.intellij.openapi.util.registry.Registry.get("compiler.process.debug.port").setValue(5006)
    buildProject()
    val usages = service.usagesOf(target).unwrap

    expected.foreach {
      case (filename, lines) =>
        val usage = UsagesInFile(filesMap(filename), lines)
        assertTrue(
          s"Usage $usage expected, but not found in ${usages.mkString("[\n", "\t\n", "]")}",
          usages.contains(usage)
        )
    }
  }

  def testIndexUninitialized(): Unit = {
    val file =
      myFixture.configureByText(
        "Uninitialized.scala",
        s"""
           |class Foo {
           |  implicit val ${CARET}f: Int = 42
           |  def foo(implicit i: Int): Int = ???
           |  foo
           |}
       """.stripMargin
      )
    val dirtyUsages = service.usagesOf(implicitSearchTargetAtCaret)
    assertTrue("Should not find any usages if index does not exist", dirtyUsages.unwrap.isEmpty)
    buildProject()
    val usages   = service.usagesOf(implicitSearchTargetAtCaret)
    val expected = Set(UsagesInFile(file.getVirtualFile, Seq(5)))
    assertEquals(expected, usages)
  }

  def testTyping(): Unit = {
    val fileA =
      myFixture.configureByText(
        "TypingA.scala",
        s"object TypingA { trait Foo; implicit val fo${CARET}o: Ordering[Foo] = null }"
      )

    val fileB =
      myFixture.addFileToProject(
        "TypingB.scala",
        """
          |import TypingA._
          |
          |object TypingB {
          |  List.empty[Foo].sorted
          |}
        """.stripMargin
      )

    buildProject()
    val target = implicitSearchTargetAtCaret
    val scope = service.dirtyScopeForDefinition(target)
    Seq(fileA, fileB).foreach(f => assertFalse(scope.contains(f.getVirtualFile)))
    val usages = service.usagesOf(target)
    assertTrue("Unexpected empty usages.", usages.unwrap.nonEmpty)
    myFixture.openFileInEditor(fileB.getVirtualFile)
    myFixture.`type`("/* bla-bla-bla */")
    val scope2 = service.dirtyScopeForDefinition(target)
    Seq(fileA, fileB).foreach(f => assertTrue(scope2.contains(f.getVirtualFile)))
    val usages2 = service.usagesOf(target)
    assertTrue("Should not return usages from dirty scope.", usages2.unwrap.isEmpty)
  }

  def testSimple(): Unit =
    runSearchTest(
      "SimpleA.scala" ->
        s"object SimpleA { implicit val ${CARET}x: Int = 42 }",
      "SimpleB.scala" ->
        """
          |import SimpleA.x
          |object SimpleB {
          |  def f[T](implicit t: T): Unit = println(t)
          |  f[Int]
          |}
    """.stripMargin,
      "SimpleC.scala" ->
        """
          |import SimpleA.x
          |class SimpleC { println(implicitly[Int]) }
    """.stripMargin,
    )("SimpleB.scala" -> Seq(5), "SimpleC.scala" -> Seq(3))()

  def testImplicitParam(): Unit =
    runSearchTest(
      "ImplicitParam.scala" ->
        s"""
           |class ImplicitParam {
           |  implicit val impl${CARET}X: String = "foo"
           |
           |  def foo(i: Int)(implicit s: String): Unit = println(s)
           |
           |  def main(args: Array[String]): Unit = {
           |    foo(123)
           |  }
           |
           |  foo(42)
           |}
       """.stripMargin
    )("ImplicitParam.scala" -> Seq(8, 11))()

  def testImplicitConversion(): Unit =
    runSearchTest(
      "ImplicitConv.scala" ->
        s"""
           |object ImplicitConv {
           |  trait Foo { def foo(): Unit = ??? }
           |  implicit def str${CARET}ing2Foo(s: String): Foo = new Foo {}
           |  "123".foo
           |}
           |
           |object Imports { import ImplicitConv.string2Foo; "foobar".foo() }
       """.stripMargin
    )("ImplicitConv.scala" -> Seq(5, 8))()

  def testRecursiveImplicits(): Unit =
    runSearchTest(
      "RecursiveImplicitsA.scala" ->
        s"""
           |trait ToString[T] {
           |  def toS(t: T): String
           |}
           |
           |object ToString {
           |  implicit def to${CARET}StringT[T <: AnyRef]: ToString[T]                  = _.toString
           |  implicit def listToString[T](implicit ev: ToString[T]): ToString[List[T]] = _.map(ev.toS).mkString
           |
           |  implicit def tupleToString[T, U](
           |    implicit
           |    ev1: ToString[T],
           |    ev2: ToString[U]
           |  ):ToString[(T, U)] = tuple => ev1.toS(tuple._1) + ev2.toS(tuple._2)
           |}
       """.stripMargin,
      "RecursiveImplicitsB.scala" ->
        """
          |import ToString._
          |object RecursiveImplicitUsage {
          |  trait F
          |  val a = implicitly[ToString[List[(F, F)]]].toS(List.empty[(F, F)])
          |}
      """.stripMargin
    )("RecursiveImplicitsB.scala" -> Seq(5))()

  def testInheritedImplicit(): Unit =
    runSearchTest(
      "InheritedA.scala" -> s"trait InheritedA { implicit def ${CARET}x: String }",
      "InheritedB.scala" ->
        """
          |trait InheritedB extends InheritedA {
          |  def foo(implicit s: String): Unit = println(s)
          |  foo
          |}
      """.stripMargin
    )("InheritedB.scala" -> Seq(4))()

  def testImplicitClass(): Unit =
    runSearchTest(
      "ImplicitClassA.scala" ->
      s"""
         |object ImplicitClassA {
         |  implicit class Rich${CARET}Int(val x: Int) extends AnyVal {
         |    def once: Int = x
         |    def twice: Int = x * 2
         |    def thrice: Int = x * 3
         |  }
         |}
       """.stripMargin,
      "ImplicitClassB.scala" ->
      s"""
         |import ImplicitClassA.RichInt
         |object ImplicitClassB {
         |  1.once
         |  2.twice
         |  3.thrice
         |}
       """.stripMargin
    )("ImplicitClassB.scala" -> Seq(4, 5, 6))()

  def testUsageFromLibrary(): Unit =
    runSearchTest(
      "UsageFromLibrary.scala" ->
      s"""
         |import scala.collection.JavaConverters._
         |object UsageFromLibrary {
         |  Set(1, 2, 3).asJava
         |}
       """.stripMargin
    )("UsageFromLibrary.scala" -> Seq(4)) {
      val aClass = myFixture.findClass("scala.collection.convert.Decorators.AsJava")
      aClass.getMethods.find(_.getName == "asJava").get
    }

  def testPrivateThis(): Unit =
    runSearchTest(
      "PrivateThis.scala" ->
      s"""
         |object PrivateThis {
         |  trait Foo[T]
         |  private[this] implicit val ${CARET}x: Foo[Int] = null
         |
         |  implicitly[Foo[Int]]
         |}
       """.stripMargin
    )("PrivateThis.scala" -> Seq(4, 6))()

  def testApplyMethod(): Unit =
    runSearchTest(
      "ApplyMethodA.scala" ->
      s"""
         |class Foo(val x: Int, val y: Double, z: String)
         |object Foo {
         |  def ${CARET}apply(x: Int, y: Double, z: String): Option[Foo] = ???
         |  Foo(1, 2d, "42")
         |}
       """.stripMargin,
      "ApplyMethodB.scala" ->
      """
        |object Bar {
        |  val foo = Foo(42, 1d, "foo")
        |  val fooModule = Foo
        |}
      """.stripMargin
    )("ApplyMethodA.scala" -> Seq(5), "ApplyMethodB.scala" -> Seq(3))(myFixture.getElementAtCaret)

  def testSynthetic(): Unit = {
    myFixture.configureByText(
      "Synthetic.scala",
      s"""
         |trait Foo[T]
         |case class Bar[T](i: Int)(implicit val e${CARET}v: Foo[T])
       """.stripMargin
    )
    buildProject()
    val usages   = service.usagesOf(implicitSearchTargetAtCaret)
    assertTrue(s"Should not find any usages in synthetic methods: $usages.", usages.unwrap.isEmpty)
  }
}
