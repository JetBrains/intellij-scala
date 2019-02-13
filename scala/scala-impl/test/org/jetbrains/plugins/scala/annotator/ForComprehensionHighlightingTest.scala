package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

abstract class ForComprehensionHighlightingTestBase extends ScalaHighlightingTestBase {
  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+
      IvyManagedLoader("org.typelevel" %% "cats-core" % "1.5.0", "org.typelevel" %% "cats-effect" % "1.1.0")
}

class ForComprehensionHighlightingTest_2_11 extends ForComprehensionHighlightingTestBase {

  override implicit val version: ScalaVersion = Scala_2_11

  def test_guard_type(): Unit = {
    val code =
      """
        |for {x <- Seq(1) if x } {}
        |for {y <- Seq(true) if y } {}
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)){
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

    assertMatches(errorsFromScalaCode(code)){
      case Error("x", "Expression of type Boolean doesn't conform to expected type Int") :: Nil =>
    }
  }

  def test_SCL6498(): Unit = {
    val code =
      """
        |for (i <- 1 to 5 if i) 1
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)){
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

    assertMatches(errorsFromScalaCode(code)){
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

    assertNothing(errorsFromScalaCode(code))
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

    assertMatches(errorsFromScalaCode(code)){
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

    assertMatches(errorsFromScalaCode(code)){
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

    assertMatches(errorsFromScalaCode(code)){
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

    assertNothing(errorsFromScalaCode(code))
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

    assertMatches(errorsFromScalaCode(code)){
      case Error(_, "Expression of type Iterable[String] doesn't conform to expected type Option[Int]") :: Nil =>
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

    assertMatches(errorsFromScalaCode(code)){
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

    assertMatches(errorsFromScalaCode(code)){
      case Error("<-", "Cannot resolve symbol flatMap") :: Nil =>
    }
  }

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

    assertMatches(errorsFromScalaCode(code)){
      case Error("<-", "Cannot resolve symbol map") :: Error("+", "Cannot resolve symbol +") :: Nil =>
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

    assertNothing(errorsFromScalaCode(code))
  }

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
      Error("x", "Missing parameter type: x"),
      Error(">", "Cannot resolve symbol >")
    )
  }

  def test_implicitWithFilter_before_filter(): Unit = {
    val code =
      """
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
      """.stripMargin

    assertNothing(errorsFromScalaCode(code))
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
      Error("<-", "Cannot resolve symbol foreach"),
      Error("a", "Cannot resolve symbol a"),
      Error("withFilter", "Cannot resolve symbol withFilter"),
      Error("b", "Cannot resolve symbol b"),
      Error("withFilter", "Cannot resolve symbol withFilter"),
      Error("c", "Cannot resolve symbol c")
    )
  }
}


class ForComprehensionHighlightingTest_2_12 extends ForComprehensionHighlightingTestBase {

  override implicit val version: ScalaVersion = Scala_2_12

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

  def test_implicitWithFilter_before_filter(): Unit = {
    val code =
      """
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
      """.stripMargin

    assertNothing(errorsFromScalaCode(code))
  }
}

class ForComprehensionHighlightingTest_WithBetterMonadicFor extends ForComprehensionHighlightingTestBase {

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings
    newSettings.plugins = newSettings.plugins :+ "better-monadic-for"
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

    assertNothing(errorsFromScalaCode(code))
  }
}
