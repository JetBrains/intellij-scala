package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.SharedTestProjectToken
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

abstract class ScalaTypeAnnotationsCompletionTestBase extends ScalaCompletionTestBase {
  override protected def sharedProjectToken = SharedTestProjectToken.ByTestClassAndScalaSdkAndProjectLibraries(this)
}

class ScalaTypeAnnotationsCompletionTest extends ScalaTypeAnnotationsCompletionTestBase {
  def testCollectionFactory(): Unit = doCompletionTest(
    fileText =
      s"""object O {
         |  val v:$CARET = Seq.empty[String]
         |}""".stripMargin,
    resultText =
      s"""object O {
         |  val v: Seq[String]$CARET = Seq.empty[String]
         |}""".stripMargin,
    item = "Seq[String]"
  )

  def testOptionFactory(): Unit = doCompletionTest(
    fileText =
      s"""object O {
         |  val v: $CARET= Option.empty[String].to[Option]
         |}""".stripMargin,
    resultText =
      s"""object O {
         |  val v: Option[String]$CARET = Option.empty[String].to[Option]
         |}""".stripMargin,
    item = "Option[String]"
  )

  def testCompoundType(): Unit = doCompletionTest(
    fileText =
      s"""object O {
         |  val foo:$CARET = new Runnable {
         |    def helper(): Unit = ???
         |
         |    override def run(): Unit = ???
         |  }
         |}""".stripMargin,
    resultText =
      s"""object O {
         |  val foo: Runnable$CARET = new Runnable {
         |    def helper(): Unit = ???
         |
         |    override def run(): Unit = ???
         |  }
         |}""".stripMargin,
    item = "Runnable"
  )

  def testCompoundTypeWithTypeMember(): Unit = doCompletionTest(
    fileText =
      s"""trait Foo {
         |  type X
         |}
         |
         |object O {
         |  val foo:$CARET = new Foo {
         |    override type X = Int
         |
         |    def helper(x: X): Unit = ???
         |  }
         |}""".stripMargin,
    resultText =
      s"""trait Foo {
         |  type X
         |}
         |
         |object O {
         |  val foo: Foo {
         |    type X = Int
         |  }$CARET = new Foo {
         |    override type X = Int
         |
         |    def helper(x: X): Unit = ???
         |  }
         |}""".stripMargin,
    item =
      s"""Foo {
         |  type X = Int
         |}""".stripMargin
  )

  def testCompoundTypeWithMultipleTypeMembers(): Unit = doCompletionTest(
    fileText =
      s"""trait Foo {
         |  type X
         |  type Y
         |  type Z
         |}
         |
         |object O {
         |  val foo:$CARET = new Foo {
         |    override type X = Int
         |    override type Y = String
         |    override type Z = Boolean
         |
         |    def helper(x: X): Unit = ???
         |  }
         |}""".stripMargin,
    resultText =
      s"""trait Foo {
         |  type X
         |  type Y
         |  type Z
         |}
         |
         |object O {
         |  val foo: Foo {
         |    type X = Int
         |
         |    type Y = String
         |
         |    type Z = Boolean
         |  }$CARET = new Foo {
         |    override type X = Int
         |    override type Y = String
         |    override type Z = Boolean
         |
         |    def helper(x: X): Unit = ???
         |  }
         |}""".stripMargin,
    item =
      s"""Foo {
         |  type X = Int
         |
         |  type Y = String
         |
         |  type Z = Boolean
         |}""".stripMargin
  )

  def testInfixType(): Unit = doCompletionTest(
    fileText =
      s"""trait A
         |trait B
         |
         |object O {
         |  def foo(): =:=[A, <:<[B, =:=[=:=[B, B], A]]] = ???
         |
         |  val bar:$CARET = foo()
         |}""".stripMargin,
    resultText =
      s"""trait A
         |trait B
         |
         |object O {
         |  def foo(): =:=[A, <:<[B, =:=[=:=[B, B], A]]] = ???
         |
         |  val bar: A =:= (B <:< (B =:= B =:= A))$CARET = foo()
         |}""".stripMargin,
    item = "A =:= (B <:< (B =:= B =:= A))"
  )

  def testInfixDifferentAssociativity(): Unit = doCompletionTest(
    fileText =
      s"""trait +[A, B]
         |trait ::[A, B]
         |trait A
         |
         |object O {
         |  def foo(): ::[+[A, +[::[A, A], A]], +[A, ::[A, A]]] = ???
         |
         |  val bar:$CARET = foo()
         |}""".stripMargin,
    resultText =
      s"""trait +[A, B]
         |trait ::[A, B]
         |trait A
         |
         |object O {
         |  def foo(): ::[+[A, +[::[A, A], A]], +[A, ::[A, A]]] = ???
         |
         |  val bar: (A + ((A :: A) + A)) :: (A + (A :: A))$CARET = foo()
         |}""".stripMargin,
    item = "(A + ((A :: A) + A)) :: (A + (A :: A))"
  )

  def testTupledFunction(): Unit = doCompletionTest(
    fileText =
      s"""class Test {
         |  def g(f: (String, Int) => Unit): Unit = {
         |    val t:$CARET = f.tupled
         |  }
         |}""".stripMargin,
    resultText =
      s"""class Test {
         |  def g(f: (String, Int) => Unit): Unit = {
         |    val t: ((String, Int)) => Unit$CARET = f.tupled
         |  }
         |}""".stripMargin,
    item = "((String, Int)) => Unit"
  )
}

class ScalaTypeAnnotationsCompletionTest_with_2_12 extends ScalaTypeAnnotationsCompletionTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= ScalaVersion.Latest.Scala_2_12

  def testShowAsInfixAnnotation(): Unit = doCompletionTest(
    fileText =
      s"""import scala.annotation.showAsInfix
         |
         |@showAsInfix class Map[A, B]
         |
         |object O {
         |  def foo(): Map[Int, Map[Int, String]] = ???
         |
         |  val bar:$CARET = foo()
         |}""".stripMargin,
    resultText =
      s"""import scala.annotation.showAsInfix
         |
         |@showAsInfix class Map[A, B]
         |
         |object O {
         |  def foo(): Map[Int, Map[Int, String]] = ???
         |
         |  val bar: Int Map (Int Map String)$CARET = foo()
         |}""".stripMargin,
    item = "Int Map (Int Map String)"
  )
}

class ScalaTypeAnnotationsCompletionTest_with_kind_projector extends ScalaTypeAnnotationsCompletionTestBase {
  override def setUp(): Unit = {
    super.setUp()
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "kind-projector"
    )
    defaultProfile.setSettings(newSettings)
  }

  def testTypeLambdaInline(): Unit = doCompletionTest(
    fileText =
      s"""object O {
         |  def foo: ({type L[A] = Either[String, A]})#L
         |
         |  val v:$CARET = foo
         |}""".stripMargin,
    resultText =
      s"""object O {
         |  def foo: ({type L[A] = Either[String, A]})#L
         |
         |  val v: Either[String, ?]$CARET = foo
         |}""".stripMargin,
    item = "Either[String, ?]"
  )

  def testTypeLambda(): Unit = doCompletionTest(
    fileText =
      s"""object O {
         |  def foo: ({type L[F[_]] = F[Int]})#L
         |
         |  val v:$CARET = foo
         |}""".stripMargin,
    resultText =
      s"""object O {
         |  def foo: ({type L[F[_]] = F[Int]})#L
         |
         |  val v: Lambda[F[_] => F[Int]]$CARET = foo
         |}""".stripMargin,
    item = "Lambda[F[_] => F[Int]]"
  )
}
