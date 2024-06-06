package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}
import org.jetbrains.plugins.scala.annotator.Message._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

abstract class ForComprehensionHighlightingTestBase extends ScalaHighlightingTestBase

class ForComprehensionHighlightingTest extends ForComprehensionHighlightingTestBase {


  def test_guard_type(): Unit = {
    val code =
      """
        |for {x <- Seq(1) if x } {}
        |for {y <- Seq(true) if y } {}
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("if", "Cannot resolve overloaded method 'withFilter'") ::
        Error("x", "Expression of type Int doesn't conform to expected type Boolean") ::
        Nil =>
    }
  }

  def test_guard_with_custom_type(): Unit = {
    val code =
      """
        |class A[T] {
        |  def withFilter(f: T => Int): A[T] = ???
        |  def foreach(f: T => Unit): Unit = ???
        |}
        |for {x <- new A[Boolean] if x } {}
        |for {y <- new A[Int] if y } {}
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("x", "Expression of type Boolean doesn't conform to expected type Int") :: Nil =>
    }
  }

  def test_SCL6498(): Unit = {
    val code =
      """
        |for (i <- 1 to 5 if i) 1
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("if", "Cannot resolve overloaded method 'withFilter'") ::
        Error("i", "Expression of type Int doesn't conform to expected type Boolean") ::
        Nil =>
    }
  }

  def test_monadic_context_SCL14401(): Unit = {
    val code =
      """
        |import scala.concurrent.Future
        |import scala.concurrent.ExecutionContext
        |implicit val ec = ExecutionContext.global
        |
        |val forComp = for {
        |    x <- Future("hello1")
        |    y <- Option("hello2")
        |  } yield "blah"
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("y <- Option(\"hello2\")", "Expression of type Option[String] doesn't conform to expected type Future[S_]") :: Nil =>
    }
  }


  def test_monadic_context_seq_option(): Unit = {
    val code =
      """
        |val forComp = for {
        |    x <- Seq(1, 2)
        |    x <- Option("hello1")
        |  } yield "blah"
      """.stripMargin

    assertNoErrors(code)
  }

  def test_monadic_context_option_seq(): Unit = {
    val code =
      """
        |
        |val forComp = for {
        |    x <- Option("hello1")
        |    x <- Seq(1, 2)
        |  } yield "blah"
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("x <- Seq(1, 2)", "Expression of type Seq[String] doesn't conform to expected type Option[B_]") :: Nil =>
    }
  }

  // SCL-14734
  def test_missing_for_operator(): Unit = {
    val code =
      """
        |for (i <- Unit) i
        |for (i <- Unit) yield i
        |for (i <- Unit; j <- Unit) yield (i, j)
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("<-", "Cannot resolve symbol foreach") ::
        Error("<-", "Cannot resolve symbol map") ::
        Error("<-", "Cannot resolve symbol flatMap") ::
        Error("<-", "Cannot resolve symbol map") ::
        Nil =>
    }
  }

  def test_missing_withFilter(): Unit = {
    val code =
      """
        |for (i <- Unit if true) {}
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("if", "Cannot resolve symbol withFilter") :: Nil =>
    }
  }

  def test_SCL5638(): Unit = {
    val code =
      """
        |val l = List(1,2,3)
        |val o = Some("jam")
        |val s = Set('a', 'b')
        |for(x <- l; y <- o) yield "Got one"
        |for(x <- o; y <- l) yield "Got one" // no error highlighting in intellij, but fails to compile
        |for(x <- l; y <- s) yield 3.14
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("y <- l", "Expression of type List[String] doesn't conform to expected type Option[B_]") :: Nil =>
    }
  }

  def test_SCL14618(): Unit = {
    val code =
      """
        |import scala.collection.mutable
        |
        |class CFG(/*...*/)
        |{
        |    /*...*/
        |    def foreach[U](f: this.CFGNode => U): Unit = ???
        |    class CFGNode(/*...*/) { /*...*/ }
        |}
        |
        |/* In another file */
        |val cfg = new CFG(/*...*/)
        |val buf = mutable.ListBuffer.empty[cfg.CFGNode]
        |for (node <- cfg) buf += node
      """.stripMargin

    assertNoErrors(code)
  }

  def test_SCL14184(): Unit = {
    val code =
      """
        |val x: Option[Int] =
        |  for {
        |    a <- Option("a")
        |    if a == "a"
        |    b <- Option("b")
        |  } yield b
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error(_, "Expression of type Option[String] doesn't conform to expected type Option[Int]") ::
        Error(_, "Expression of type Option[String] doesn't conform to expected type Option[Int]") ::
        Nil =>
    }
  }

  def test_SCL9901(): Unit = {
    val code =
      """
        |val optList = Option(List(1,2,3,4,5))
        |for{
        |  lst <- optList
        |  elem <- lst
        |} yield elem + 1
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error(_, "Expression of type List[Int] doesn't conform to expected type Option[B_]") :: Nil =>
    }
  }

  def test_missing_method_downchain(): Unit = {
    val code =
      """
        |class Fun[T] {
        |  def foreach(f: T => Unit): Unit = ???
        |  def withFilter(f: T => Boolean): Fun[T] = ???
        |}
        |
        |for {
        |  x <- new Fun[Int] if true
        |  y <- List(3, 3)
        |} yield {}
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("<-", "Cannot resolve symbol flatMap") :: Nil =>
    }
  }

  def test_SCL14903(): Unit = {
    val code =
      """
        |object PlaceItems {
        |  case class PopulationItem(name: String, density: Double)
        |
        |  object Population {
        |  }
        |
        |  case class Population(items: Seq[PopulationItem])
        |
        |  def populate(): Unit = {
        |    val populationsByName = Map.empty[String, Population]
        |
        |    // toSeq needed, so that multiple identical keys are supported
        |    val squaresByPopulation = Seq.empty[String]
        |
        |    // we might merge identical populations, but processing them individually should give the same result
        |
        |    val items = for {
        |      populationName <- squaresByPopulation
        |      population = populationsByName(populationName)
        |      tpe <- population.items
        |    } yield ???
        |
        |  }
        |}
      """.stripMargin

    assertNoErrors(code)
  }

  def test_implicitWithFilter_before_filter(): Unit = {
    val code =
      """object Wrapper {
        |class S[X] {
        |  def filter(f: X => Boolean): Unit = ???
        |  def foreach(f: X => Unit): Unit = ???
        |}
        |implicit class SExt[X](s: S[X]) {
        |  def withFilter(f: X => Boolean): S[X] = ???
        |}
        |
        |val s = new S[Int]
        |for {
        |  x <- s
        |  if x > 0
        |} ()
        |
        |s.filter(x => x > 0)
        |s.withFilter(x => x > 0)
        |}
      """.stripMargin

    assertNoErrors(code)
  }
}

class ForComprehensionHighlightingTest_with_cats_2_12 extends ForComprehensionHighlightingTestBase {


  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+
      IvyManagedLoader("org.typelevel" %% "cats-core" % "1.5.0", "org.typelevel" %% "cats-effect" % "1.1.0")

  def test_SCL14801(): Unit = {
    val code =
      """
        |import cats.implicits._
        |import cats.effect.IO
        |def getCounts: IO[(Int, Int)] = ???
        |for {
        |  (x, y) <- getCounts
        |} yield x + y
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)) {
      case Error("<-", "Cannot resolve symbol withFilter") :: Error("+", "Cannot resolve symbol +") :: Nil =>
    }
  }
}

class ForComprehensionHighlightingTest_without_filter extends ForComprehensionHighlightingTestBase {


  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_11

  def test_filterOnly(): Unit = {
    val code =
      """
        |class S[X] {
        |  def filter(f: X => Boolean): S[X] = ???
        |  def foreach(f: X => Unit): Unit = ???
        |}
        |
        |val s = new S[Int]
        |for {
        |  x <- s
        |  if x > 0
        |} ()
      """.stripMargin

    assertMessagesSorted(errorsFromScalaCode(code))(
      Error("if", "Cannot resolve symbol withFilter"),
      Error(">", "Cannot resolve symbol >")
    )
  }
}

class ForComprehensionHighlightingTest_with_filter extends ForComprehensionHighlightingTestBase {


  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_11

  def testSCL17260(): Unit = assertNothing(errorsFromScalaCode(
    """
      |class CustomCollection[T] {
      |    def map[R](f: T => R): CustomCollection[R] = ???
      |    def flatMap[R](f: T => CustomCollection[R]): CustomCollection[R] = ???
      |    def filter(f: T => Boolean): CustomCollection[T] = ???
      |  }
      |  for {
      |    (k, v) <- new CustomCollection[(Int, Int)]
      |  } yield (k, v)
      |""".stripMargin
  ))

  def test_filterOnly(): Unit = {
    val code =
      """
        |class S[X] {
        |  def filter(f: X => Boolean): S[X] = ???
        |  def foreach(f: X => Unit): Unit = ???
        |}
        |
        |val s = new S[Int]
        |for {
        |  x <- s
        |  if x > 0
        |} ()
        |
        |s.withFilter(x => x > 0)
      """.stripMargin

    assertMessagesSorted(errorsFromScalaCode(code))(
      Error("withFilter", "Cannot resolve symbol withFilter"),
      Error("x", "Missing parameter type"),
      Error(">", "Cannot resolve symbol >")
    )
  }

  def test_implicitWithFilter_before_filter(): Unit = {
    val code =
      """object Wrapper {
        |class S[X] {
        |  def filter(f: X => Boolean): Unit = ???
        |  def foreach(f: X => Unit): Unit = ???
        |}
        |implicit class SExt[X](s: S[X]) {
        |  def withFilter(f: X => Boolean): S[X] = ???
        |}
        |
        |val s = new S[Int]
        |for {
        |  x <- s
        |  if x > 0
        |} ()
        |
        |s.filter(x => x > 0)
        |s.withFilter(x => x > 0)
        |}
      """.stripMargin

    assertNoErrors(code)
  }

  def test_wrong_withFilter_before_filter(): Unit = {
    val code =
      """
        |class S[X] {
        |  def withFilter(f: X => Boolean, a: Boolean): Unit = ???
        |  def filter(f: X => Boolean): S[X] = ???
        |  def foreach(f: X => Unit): Unit = ???
        |}
        |
        |val s = new S[Int]
        |for {
        |  x <- s
        |  if x > 0
        |} ()
      """.stripMargin

    assertMessagesSorted(errorsFromScalaCode(code))(
      Error("<-", "Cannot resolve symbol foreach")
    )
  }

  def test_no_rewrite_withFilter(): Unit = {
    val code =
      """
        |class S[X] {
        |  def filter(f: X => Boolean): S[X] = ???
        |  def foreach(f: X => Unit): Unit = ???
        |}
        |
        |val s = new S[Int]
        |for {
        |  x <- s.withFilter(a: Int =>)
        |} ()
        |
        |for {
        |  x <- s
        |  if true || s.withFilter(b: Int =>)
        |} ()
        |
        |for {
        |  x <- s
        |} s.withFilter(c: Int =>)
      """.stripMargin

    assertMessagesSorted(errorsFromScalaCode(code))(
      Error("withFilter", "Cannot resolve symbol withFilter"),
      Error("a", "Cannot resolve symbol a"),
      Error("withFilter", "Cannot resolve symbol withFilter"),
      Error("b", "Cannot resolve symbol b"),
      Error("withFilter", "Cannot resolve symbol withFilter"),
      Error("c", "Cannot resolve symbol c")
    )
  }
}

class ForComprehensionHighlightingTest_with_BetterMonadicFor extends ForComprehensionHighlightingTestBase {


  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_2_12 && version < LatestScalaVersions.Scala_3_0

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "better-monadic-for"
    )
    defaultProfile.setSettings(newSettings)
  }

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+
      IvyManagedLoader("org.typelevel" %% "cats-core" % "1.5.0", "org.typelevel" %% "cats-effect" % "1.1.0")

  def test_SCL14801(): Unit = {
    val code =
      """
        |import cats.implicits._
        |import cats.effect.IO
        |def getCounts: IO[(Int, Int)] = ???
        |for {
        |  (x, y) <- getCounts
        |} yield x + y
      """.stripMargin

    assertNoErrors(code)
  }
}

class ForComprehensionSemicolonTest extends ForComprehensionHighlightingTestBase {

  import org.junit.Assert.assertEquals

  val errorText = ScalaBundle.message("semicolon.not.allowed.here")

  def errors(code: String) = {
    val msgs = errorsFromScalaCode(code)
    msgs.foreach { msg => assertEquals(Error(";", errorText), msg) }
    msgs.length
  }

  def test_ok(): Unit =
    assertEquals(0, errors("for(x<-Seq(1); if x==3;y = x; a <- Seq(2); if a == x) ()"))

  def test_error_semicolons(): Unit =
    assertEquals(2 + 2 + 3 + 3, errors("for(;;x<-Seq(1);;;if x==3;;;;y = x;;;) ()"))

  def test_error_semicolons_with_spaces(): Unit =
    assertEquals(2 + 2 + 3 + 3, errors("for( ; ; x <- Seq(1)  ; ; ; if x == 3 ; ; ; ; y = x ; ; ; ) ()"))

  def test_error_semicolons_with_newlines(): Unit =
    assertEquals(2 + 2 + 3 + 3, errors("for{\n; \n; \nx <- Seq(1)\n;\n;\n;\nif x == 3\n;\n;;;\n y = x\n;;\n;} ()"))
}

abstract class ForComprehensionRefutabilityTestBase_3 extends ForComprehensionHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_3

  def test_case_simple(): Unit = {
    val code =
      """
        |val list = List(List(1, 2), List(3))
        |for case head :: tail <- list do println(head > 1)
      """.stripMargin

    assertNoErrors(code)
  }

  def test_case_nested(): Unit = {
    val code =
      """
        |val list = List(List(1, 2), List(3, 4))
        |for case (_ :: (head :: tail)) <- list do println(head > 1)
      """.stripMargin

    assertNoErrors(code)
  }

  def test_missing_withFilter_WithCase(): Unit = {
    val code =
      """
        |for
        |  case (a, b) <- Right("" -> 1)
        |yield ()
      """.stripMargin

    assertMessages(code, Error("<-", "Cannot resolve symbol withFilter"))
  }

  def test_missing_withFilter(): Unit = {
    val code =
      """
        |for
        |  (a, b) <- Right("" -> 1)
        |yield ()
      """.stripMargin

    assertMessages(code, Error("<-", "Cannot resolve symbol withFilter"))
  }

  def test_scala3_block(): Unit = assertNoErrors(
    """
      |for {
      |  x <- List(1, 2)
      |  str <-
      |    val foo: List[String] = ???
      |    foo
      |  blub =
      |    val str2 = str
      |    str
      |  if
      |    val b = true
      |    b
      |} yield str
      |""".stripMargin
  )
}

class ForComprehensionRefutabilityTest_3_3 extends ForComprehensionRefutabilityTestBase_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3
}

class ForComprehensionRefutabilityTest_3_3_future extends ForComprehensionRefutabilityTestBase_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      additionalCompilerOptions = defaultProfile.getSettings.additionalCompilerOptions :+ "-source:future"
    )
    defaultProfile.setSettings(newSettings)
  }

  override def test_missing_withFilter(): Unit = {
    val code =
      """
        |for
        |  (a, b) <- Right("" -> 1)
        |yield ()
      """.stripMargin

    assertNoErrors(code)
  }
}

class ForComprehensionRefutabilityTest_From_3_4 extends ForComprehensionRefutabilityTestBase_3 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_4

  override def test_missing_withFilter(): Unit = {
    val code =
      """
        |for
        |  (a, b) <- Right("" -> 1)
        |yield ()
      """.stripMargin

    assertNoErrors(code)
  }
}
