package org.jetbrains.plugins.scala
package lang.typeInference

import com.intellij.lang.annotation.HighlightSeverity
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.typeInference.Scala3ExtensionPolymorphicQualifierTest.withClassState
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * An extension method invoked on a qualifier whose type parameters are not inferred yet,
 * such as `get` in `get.flatMap(...)` with `def get[T]: State[T, T]`.
 */
@Category(Array(classOf[TypecheckerTests]))
class Scala3ExtensionPolymorphicQualifierTest extends TypeInferenceTestBase {
  override def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3_9

  override def checkTextHasNoErrors(@Language("Scala 3") text: String): Unit =
    super.checkTextHasNoErrors(text)

  private def checkTypeAndNoErrors(@Language("Scala 3") text: String): Unit = {
    doTest(text)
    myFixture.testHighlighting(false, false, false, getFile.getVirtualFile)
  }

  // SCL-22528
  def testSCL22528_forComprehension(): Unit = checkTextHasNoErrors(
    withClassState(
      """def modify[S](f: S => S): State[S, Unit] = {
        |  for {
        |    s <- get
        |    _ <- set(f(s))
        |  } yield ()
        |}
        |""".stripMargin
    )
  )

  // SCL-22528
  def testSCL22528_desugared(): Unit = checkTextHasNoErrors(
    withClassState(
      """def modify2[S](f: S => S): State[S, Unit] = {
        |  get
        |    .flatMap(s =>
        |      set(f(s))
        |        .map(_ => ())
        |    )
        |}
        |""".stripMargin
    )
  )

  // SCL-22528, SCL-23218
  def testSCL22528_opaqueType(): Unit = checkTypeAndNoErrors(
    s"""opaque type State[S, +A] = S => (A, S)
       |
       |object State {
       |  extension [S, A](underlying: State[S, A]) {
       |    def run(s: S): (A, S) = underlying(s)
       |
       |    def flatMap[B](f: A => State[S, B]): State[S, B] = { s =>
       |      val (a, s1) = underlying(s)
       |      f(a)(s1)
       |    }
       |
       |    def map[B](f: A => B): State[S, B] = flatMap(a => unit(f(a)))
       |  }
       |
       |  def unit[S, A](a: A): State[S, A] = s => (a, s)
       |  def get[S]: State[S, S] = s => (s, s)
       |  def set[S](s: S): State[S, Unit] = _ => ((), s)
       |
       |  def modify[S](f: S => S): State[S, Unit] = {
       |    for {
       |      s <- get
       |      _ <- set(f(${START}s${END}))
       |    } yield ()
       |  }
       |}
       |
       |//S
       |""".stripMargin
  )

  // SCL-22528, the extension receiver keeps the qualifier's type parameter
  def testSCL22528_lambdaParameterType(): Unit = checkTextHasNoErrors(
    withClassState(
      """def modify3[S](f: S => S): State[S, Unit] = {
        |  get.flatMap(s => set(f(s)))
        |}
        |""".stripMargin
    )
  )

  // SCL-22528, the receiver shapes of the comment of 2025-08-04 that infer the qualifier's type parameter
  def testSCL22528_receiverShapes(): Unit = checkTextHasNoErrors(
    """trait State[S, +A]
      |object State {
      |  extension [S, A](s: State[S, A]) {
      |    def process1[B](f: A => State[S, B]): State[S, B] = ???
      |  }
      |  extension [S](s: State[S, Any]) {
      |    def process2[B](f: Any => State[S, B]): State[S, B] = ???
      |  }
      |  extension [A](s: State[Any, A]) {
      |    def process3[B](f: A => State[Any, B]): State[Any, B] = ???
      |  }
      |  extension (s: State[Any, Any]) {
      |    def process4[B](f: Any => State[Any, B]): State[Any, B] = ???
      |  }
      |
      |  def get[S]: State[S, S] = ???
      |  def set[S](s: S): State[S, Unit] = ???
      |
      |  def modify[S](f: S => S): State[S, Unit] = get.process1(s => set(f(s)))
      |
      |  get[String].process1(s => ???)
      |  get[Any].process1(s => ???)
      |
      |  get[String].process2(s => ???)
      |  get[Any].process2(s => ???)
      |  get[Nothing].process2(s => ???)
      |  get.process2(s => ???)
      |
      |  get[Any].process3(s => ???)
      |  get[Any].process4(s => ???)
      |}
      |""".stripMargin
  )

  // SCL-22528
  // the same code with regular methods, a control
  def testSCL22528_regularMethods(): Unit = checkTextHasNoErrors(
    """class State[S, +A] {
      |  def flatMap[B](f: A => State[S, B]): State[S, B] = ???
      |  def map[B](f: A => B): State[S, B] = ???
      |}
      |
      |object State {
      |  def get[S]: State[S, S] = ???
      |  def set[S](s: S): State[S, Unit] = ???
      |
      |  def modify[S](f: S => S): State[S, Unit] = {
      |    for {
      |      s <- get
      |      _ <- set(f(s))
      |    } yield ()
      |  }
      |}
      |""".stripMargin
  )

  // SCL-22528
  def testGeneratorParameterType(): Unit = checkTypeAndNoErrors(
    withClassState(
      s"""def modify[S](f: S => S): State[S, Unit] = {
         |  for {
         |    s <- get
         |    _ <- set(f(${START}s${END}))
         |  } yield ()
         |}
         |""".stripMargin
    ) + "\n//S"
  )

  // SCL-22528
  def testLambdaParameterType(): Unit = checkTypeAndNoErrors(
    withClassState(
      s"""def modify[S](f: S => S): State[S, Unit] = {
         |  get.flatMap(s => set(f(${START}s${END})))
         |}
         |""".stripMargin
    ) + "\n//S"
  )

  // SCL-22528
  def testCallResultType(): Unit = checkTypeAndNoErrors(
    withClassState(
      s"""def modify[S](f: S => S): State[S, Unit] = {
         |  ${START}get.flatMap(s => set(f(s)))${END}
         |}
         |""".stripMargin
    ) + "\n//State[S, Unit]"
  )

  // SCL-22528
  def testInferenceWithoutExpectedResultType(): Unit = checkTypeAndNoErrors(
    withClassState(
      s"""def modify[S](f: S => S) = {
         |  ${START}get.flatMap((s: S) => set(f(s)))${END}
         |}
         |""".stripMargin
    ) + "\n//State[S, Unit]"
  )

  // SCL-22528
  def testImportedExtension(): Unit = checkTypeAndNoErrors(
    s"""class State[S, +A]
       |object Syntax {
       |  extension [S, A](state: State[S, A]) {
       |    def bind[B](f: A => State[S, B]): State[S, B] = ???
       |  }
       |}
       |import Syntax.*
       |def get[S]: State[S, S] = ???
       |def put[S](s: S): State[S, Int] = ???
       |def modify[S](f: S => S): State[S, Int] = {
       |  ${START}get.bind(s => put(f(s)))${END}
       |}
       |//State[S, Int]
       |""".stripMargin
  )

  // SCL-22528
  def testTwoQualifierTypeParameters(): Unit = checkTypeAndNoErrors(
    s"""class Entry[K, V]
       |object Entry {
       |  extension [Key, Value](entry: Entry[Key, Value]) {
       |    def mapValue[W](f: Value => W): Entry[Key, W] = ???
       |  }
       |  def empty[InitialKey, InitialValue]: Entry[InitialKey, InitialValue] = ???
       |  def transform[K, V](f: V => String): Entry[K, String] = {
       |    ${START}empty.mapValue(f)${END}
       |  }
       |}
       |//Entry[K, String]
       |""".stripMargin
  )

  // SCL-22528
  def testBoundedQualifier(): Unit = checkTypeAndNoErrors(
    s"""class State[S <: CharSequence, +A]
       |object State {
       |  extension [E <: CharSequence, A](state: State[E, A]) {
       |    def bind[B](f: A => State[E, B]): State[E, B] = ???
       |  }
       |  def get[T <: CharSequence]: State[T, T] = ???
       |  def size[S <: CharSequence](s: S): State[S, Int] = ???
       |  def modify[S <: CharSequence](f: S => S): State[S, Int] = {
       |    ${START}get.bind(s => size(f(s)))${END}
       |  }
       |}
       |//State[S, Int]
       |""".stripMargin
  )

  // SCL-22528
  def testMethodApplicationQualifier(): Unit = checkTypeAndNoErrors(
    withClassState(
      s"""def read[S](): State[S, S] = ???
         |def modify[S](f: S => S): State[S, Unit] = {
         |  ${START}read().flatMap(s => set(f(s)))${END}
         |}
         |""".stripMargin
    ) + "\n//State[S, Unit]"
  )

  // SCL-22528
  def testExplicitTypeArgumentControl(): Unit = checkTypeAndNoErrors(
    withClassState(
      s"""def modify[S](f: S => S): State[S, Unit] = {
         |  ${START}get[S].flatMap(s => set(f(s)))${END}
         |}
         |""".stripMargin
    ) + "\n//State[S, Unit]"
  )

  // SCL-22528
  def testRegularMemberTakesPrecedence(): Unit = checkTypeAndNoErrors(
    s"""class MemberResult
       |class ExtensionResult
       |class Box[A] {
       |  def choose(f: A => A): MemberResult = ???
       |}
       |object Box {
       |  extension [A](box: Box[A]) {
       |    def choose(f: A => A): ExtensionResult = ???
       |  }
       |  def empty[A]: Box[A] = ???
       |  def use[A](f: A => A): MemberResult = {
       |    ${START}empty.choose(f)${END}
       |  }
       |}
       |//MemberResult
       |""".stripMargin
  )

  // SCL-22528
  def testImplicitConversionFallback(): Unit = checkTypeAndNoErrors(
    s"""import scala.language.implicitConversions
       |class Box[A]
       |class Ops {
       |  def converted: Int = ???
       |}
       |given Conversion[Box[Nothing], Ops] = ???
       |def fresh[A]: Box[A] = ???
       |val result = ${START}fresh.converted${END}
       |//Int
       |""".stripMargin
  )

  // SCL-22528
  def testIncompatibleArgumentIsStillRejected(): Unit = {
    configureScala3FromFileText(
      withClassState(
        """val invalid: State[String, Unit] = {
          |  get.flatMap(s => set(<caret>42))
          |}
          |""".stripMargin
      )
    )
    val caretOffset = getEditor.getCaretModel.getOffset
    val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).asScala
    assertTrue(
      s"Expected a type error at the incompatible argument, got: ${errors.mkString(", ")}",
      errors.exists(info => info.getStartOffset <= caretOffset && caretOffset < info.getEndOffset)
    )
  }
}

object Scala3ExtensionPolymorphicQualifierTest {
  private[lang] def withClassState(@Language("Scala 3") body: String): String = {
    // Drop trailing blank lines before computing the common indent.
    val normalizedBody = body.stripTrailing().stripIndent()
    val indentedBody = normalizedBody.indent(2).stripTrailing()
    //language=Scala 3
    s"""class State[S, +A]
       |object State {
       |  extension [S, A](underlying: State[S, A]) {
       |    def flatMap[B](f: A => State[S, B]): State[S, B] = ???
       |    def map[B](f: A => B): State[S, B] = ???
       |  }
       |
       |  def get[T]: State[T, T] = ???
       |  def set[S](s: S): State[S, Unit] = ???
       |
       |$indentedBody
       |}
       |""".stripMargin
  }
}

class Scala3ExtensionPolymorphicQualifierTest_LTS extends Scala3ExtensionPolymorphicQualifierTest {
  override def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3_3
}
