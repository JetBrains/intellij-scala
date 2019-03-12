package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SearchTargetExtractors.ShouldBeSearchedInBytecode
import org.junit.Assert._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings


class CompilerServiceUsagesTest extends ScalaCompilerReferenceServiceFixture {
  import com.intellij.testFramework.fixtures.CodeInsightTestFixture.{CARET_MARKER => CARET}

  private def searchTarget: PsiElement = {
    val targetExtractor = new ShouldBeSearchedInBytecode(new CompilerIndicesSettings(getProject))

    myFixture.getElementAtCaret match {
      case targetExtractor(t, _) => t
      case e                     => org.junit.Assert.fail(s"invalid search target, but was $e"); ???
    }
  }

  private def runCompilerIndicesTest(
    files:           Seq[(String, String)],
    expectedResults: Seq[(String, Seq[Int])],
    indicesQuery:    (ScalaCompilerReferenceService, PsiElement) => Set[Timestamped[UsagesInFile]],
    target:          =>PsiElement = searchTarget,
  ): Unit = {
    val (targetFile, rest) = (files.head, files.tail)

    val filesMap = rest.map {
      case (name, code) =>
        val psiFile = myFixture.addFileToProject(name, code)
        name -> psiFile.getVirtualFile
    }.toMap + {
      val file = myFixture.configureByText(targetFile._1, targetFile._2)
      targetFile._1 -> file.getVirtualFile
    }

//    com.intellij.compiler.server.BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
//    com.intellij.openapi.util.registry.Registry.get("compiler.process.debug.port").setValue(5006)
    buildProject()
    val service = ScalaCompilerReferenceService(getProject)
    val usages  = indicesQuery(service, target).map(_.unwrap)

    expectedResults.foreach {
      case (filename, lines) =>
        val usage = UsagesInFile(filesMap(filename), lines)
        assertTrue(
          s"Usage $usage expected, but not found in ${usages.mkString("[\n", "\t\n", "]")}",
          usages.contains(usage)
        )
    }
  }

  private def runSearchTest(
    files:    (String, String)*
  )(expected: (String, Seq[Int])*)(target: => PsiElement = searchTarget): Unit =
    runCompilerIndicesTest(files, expected, (service, e) => service.usagesOf(e), target)

  private def runInheritorsTest(
    files:    (String, String)*
  )(expected: (String, Seq[Int])*)(target: => PsiElement = searchTarget): Unit =
    runCompilerIndicesTest(
      files,
      expected,
      (service, e) => service.SAMImplementationsOf(e.asInstanceOf[PsiClass], checkDeep = false),
      target
    )

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
    val service     = ScalaCompilerReferenceService(getProject)
    val dirtyUsages = service.usagesOf(searchTarget)
    assertTrue("Should not find any usages if index does not exist", dirtyUsages.isEmpty)
    buildProject()
    val usages   = service.usagesOf(searchTarget).map(_.unwrap)
    val expected = Set(UsagesInFile(file.getVirtualFile, Seq(5)))
    assertEquals(expected, usages)
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
    val service = ScalaCompilerReferenceService(getProject)
    val usages  = service.usagesOf(searchTarget)
    assertTrue(s"Should not find any usages in synthetic methods: $usages.", usages.isEmpty)
  }

  def testInstanceUnapply(): Unit =
    runSearchTest(
      "InstanceUnapply.scala" ->
      s"""
         |class Foo(val x: Int) {
         |  def una${CARET}pply(s: String): Option[Int] = Option(x)
         |}
       """.stripMargin,
      "UsesInstanceUnapply.scala" ->
      s"""
         |object Example {
         |  val foo = new Foo(123)
         |  "456" match {
         |    case foo(num) => num
         |    case _        => 123
         |  }
         |}
       """.stripMargin
    )("UsesInstanceUnapply.scala" -> Seq(5))()

  def testInstanceApply(): Unit =
    runSearchTest(
      "InstanceApply.scala" ->
      s"""
         |class MyFunction[T, R](val f: T => R) {
         |  def ap${CARET}ply(t: T): R = f(t)
         |}
       """.stripMargin,
      "UsesInstanceApply.scala" ->
      s"""
         |object Example {
         | val fun = new MyFunction[String, Int](_.length)
         | fun("a string")
         |}
       """.stripMargin
    )("UsesInstanceApply.scala" -> Seq(4))()

  def testFlatMap(): Unit =
    runSearchTest(
      "FlatMap.scala" ->
      s"""
         |object A {
         |  final case class IO[A](a: A) {
         |    def map[B](f: A => B): IO[B] = ???
         |    def flat${CARET}Map[B](f: A => IO[B]): IO[B] = ???
         |    def withFilter(pred: A => Boolean): IO[A] = ???
         |  }
         |  val a = new IO(123)
         |  val c = new IO(678)
         |  val b = new IO(456)
         |
         |  for {
         |     ai <- a
         |     ci <- c
         |     bi <- b
         |  } yield ai + bi + ci
         |}
       """.stripMargin,
    )("FlatMap.scala" -> Seq(13, 14))()

  def testMap(): Unit =
    runSearchTest(
      "Map.scala" ->
        s"""
           |object A {
           |  final case class IO[A](a: A) {
           |    def m${CARET}ap[B](f: A => B): IO[B] = ???
           |    def flatMap[B](f: A => IO[B]): IO[B] = ???
           |    def withFilter(pred: A => Boolean): IO[A] = ???
           |  }
           |  val a = new IO(123)
           |  val b = new IO(456)
           |
           |  for {
           |     ai <- a
           |     bi <- b
           |  } yield ai + bi
           |}
       """.stripMargin,
    )("Map.scala" -> Seq(13))()

  def testWithFilter(): Unit =
    runSearchTest(
      "WithFilter.scala" ->
        s"""
           |object A {
           |  final case class IO[A](a: A) {
           |    def map[B](f: A => B): IO[B] = ???
           |    def flatMap[B](f: A => IO[B]): IO[B] = ???
           |    def with${CARET}Filter(pred: A => Boolean): IO[A] = ???
           |  }
           |
           |  val a = new IO((123, 123))
           |  val b = new IO((456, 456))
           |
           |  for {
           |     ai <- a
           |     if ai._1 > 10
           |     (bi1, bi2) <- b
           |  } yield bi1 + ai._1
           |}
       """.stripMargin,
    )("WithFilter.scala" -> Seq(14, 15))()

  def testSAMLambda(): Unit =
    runInheritorsTest(
      "SAMLambda.scala" ->
      s"""
         |trait Event
         |trait O${CARET}nClick {
         |  def onClick(event: Event): Unit
         |}
         |
         |object SAMLambda {
         |  def registerOnClickHandler(handler: OnClick): Unit = ()
         |  registerOnClickHandler(event => println(123))
         |}
       """.stripMargin
    )("SAMLambda.scala" -> Seq(9))()

  def testRunnable(): Unit =
    runInheritorsTest(
      "Runnable.scala" ->
      s"""
         |object Runnable {
         |  val th = new Thread(() => println(123))
         |}
       """.stripMargin
    )("Runnable.scala" -> Seq(3))(myFixture.findClass("java.lang.Runnable"))
}
