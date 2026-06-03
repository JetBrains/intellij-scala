package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.LatestScalaVersions.{Scala_2_11, Scala_2_12, Scala_2_13}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions.assertMatches
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class EmptyParamEtaExpansionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected def errorMessages(code: String): List[Message] = {
    val annotator = new ScalaAnnotator()

    myFixture.configureByText("a.scala", code)
    val file = myFixture.getFile.asInstanceOf[ScalaFile]

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)
    file.depthFirst().foreach(annotator.annotate)
    mock.errorAnnotations.filterNot(_.message == null)
  }
}

abstract class EmptyParamEtaExpansionTest_Since_2_11 extends EmptyParamEtaExpansionTestBase {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_11

  protected val SCL18172_Code = """
    |import java.util.concurrent.{ExecutorService, Future}
    |type Par[A] = ExecutorService => Future[A]
    |def join[A](a: Par[Par[A]]): Par[A] = es => {
    |  val value: Par[A] = a(es).get
    |  ???
    |}
    |""".stripMargin
  def testSCL18172(): Unit =
    assertMatches(errorMessages(SCL18172_Code)) {
      case Error("a(es).get", "Expression of type () => Par[A] doesn't conform to expected type Par[A]") :: Nil =>
    }

  def testSCL18525(): Unit = checkTextHasNoErrors(
    """
      |class Bug01 {
      |  def get(): Int = 101
      |}
      |
      |object Bug01 {
      |
      |  def invoke(f: () => Int): Int = f()
      |
      |  def main(args: Array[String]): Unit = {
      |    println(
      |      invoke(
      |        new Bug01().get
      |      )
      |    )
      |  }
      |}
      |""".stripMargin
  )

  def testSCL18589(): Unit = checkTextHasNoErrors(
    """
      |def helloWorld(): Unit = println(s"  function helloWorld called")
      |def callFunc(func: () => Unit) = func()
      |callFunc(helloWorld)
      |""".stripMargin
  )

  def testAutoExpansionWithImplicitParameterClauses(): Unit = checkTextHasNoErrors(
    """trait MyTrait0Methods[T]
      |trait MyTrait1Methods[T] { def foo1(x: Int): Int }
      |trait MyTrait2Methods[T] { def foo1(x: Int): Int ; def foo2(x: Int): Int }
      |
      |final class Builder[T] {
      |  def build0(implicit tc: Context): MyTrait0Methods[T] = ???
      |  def build1(implicit tc: Context): MyTrait1Methods[T] = ???
      |  def build2(implicit tc: Context): MyTrait2Methods[T] = ???
      |}
      |
      |trait Context
      |
      |object Scope {
      |  implicit def defaultContext: Context = ???
      |  def builder[T]: Builder[T] = ???
      |
      |  val encoder0: MyTrait0Methods[String] = builder.build0
      |  val encoder1: MyTrait1Methods[String] = builder.build1
      |  val encoder2: MyTrait2Methods[String] = builder.build2
      |}
      |""".stripMargin
  )
}

abstract class EmptyParamEtaExpansionTest_Since_2_12 extends EmptyParamEtaExpansionTest_Since_2_11 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_12
}

abstract class EmptyParamEtaExpansionTest_Since_2_13 extends EmptyParamEtaExpansionTest_Since_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13

  override def testSCL18172(): Unit =
    checkTextHasNoErrors(SCL18172_Code)
}

@Category(Array(classOf[TypecheckerTests]))
class EmptyParamEtaExpansion_2_12 extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_12

  def testSCL18172(): Unit = checkHasErrorAroundCaret(
    s"""
       |import java.util.concurrent.{ExecutorService, Future}
       |type Par[A] = ExecutorService => Future[A]
       |def join[A](a: Par[Par[A]]): Par[A] = es => {
       |  val value: Par[A] = ${CARET}a(es).get
       |  ???
       |}""".stripMargin
  )
}

class EmptyParamEtaExpansionTest_2_11 extends EmptyParamEtaExpansionTest_Since_2_11 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_11
}

class EmptyParamEtaExpansionTest_2_12 extends EmptyParamEtaExpansionTest_Since_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_12
}

class EmptyParamEtaExpansionTest_2_13 extends EmptyParamEtaExpansionTest_Since_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_13
}

class EmptyParamEtaExpansionTest_3 extends EmptyParamEtaExpansionTest_Since_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testAutoExpansionWithImplicitParameterClauses_UsingAndGiven(): Unit = checkTextHasNoErrors(
    """trait MyTrait0Methods[T]
      |trait MyTrait1Methods[T] { def foo1(x: Int): Int }
      |trait MyTrait2Methods[T] { def foo1(x: Int): Int ; def foo2(x: Int): Int }
      |
      |final class Builder[T] {
      |  def build0(using tc: Context): MyTrait0Methods[T] = ???
      |  def build1(using tc: Context): MyTrait1Methods[T] = ???
      |  def build2(using tc: Context): MyTrait2Methods[T] = ???
      |}
      |
      |trait Context
      |
      |object Scope {
      |  given defaultContext: Context = ???
      |  def builder[T]: Builder[T] = ???
      |
      |  val encoder0: MyTrait0Methods[String] = builder.build0
      |  val encoder1: MyTrait1Methods[String] = builder.build1
      |  val encoder2: MyTrait2Methods[String] = builder.build2
      |}
      |""".stripMargin
  )
}