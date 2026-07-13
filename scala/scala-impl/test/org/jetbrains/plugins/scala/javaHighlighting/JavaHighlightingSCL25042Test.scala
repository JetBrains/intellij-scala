package org.jetbrains.plugins.scala.javaHighlighting

import org.jetbrains.plugins.scala.ScalaVersion

//SCL-25042
class JavaHighlightingSCL25042Test extends JavaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  //language=Scala
  private val CommonScalaDefinitions =
    """import scala.reflect.runtime.universe.TypeTag
      |
      |/** A dependency-free stand-in for org.apache.spark.sql.Encoder. */
      |trait Encoder[T]
      |object Encoders {
      |  def product[T <: Product: TypeTag]: Encoder[T] = new Encoder[T] {}
      |}
      |
      |/** The important part of the reproducer: Encoder is inherited by the companion object. */
      |class SparkModel[A <: Product: TypeTag] {
      |  implicit final val encoder: Encoder[A] = Encoders.product[A]
      |  def value: A = null.asInstanceOf[A]
      |  var current: A = ???
      |  def transform(value: A): A = ???
      |  def select[B <: A](value: B): A = ???
      |}
      |
      |case class User(name: String, email: String)
      |object User extends SparkModel[User]
      |""".stripMargin

  def testStaticForwarder_GenericReturnType_TypeArgumentPreserved(): Unit = {
    assertNoErrorsInJava(
      CommonScalaDefinitions,
      """public class Usage {
        |    Encoder<User> encoder = User.encoder();
        |}
        |""".stripMargin,
      javaClassName = "Usage"
    )
  }

  def testStaticForwarder_TypeParameterReturnType_AssignableToBound(): Unit = {
    assertNoErrorsInJava(
      CommonScalaDefinitions,
      """import scala.Product;
        |
        |public class Usage {
        |    User user = null;
        |
        |    void usage1(String[] args) {
        |        Encoder<User> encoder = User.encoder();
        |        Product value = User.value();
        |        Product current = User.current();
        |        Product transform = User.transform(user);
        |        Product select = User.select(user);
        |        User.current_$eq(user);
        |    }
        |}
        |""".stripMargin,
      javaClassName = "Usage"
    )
  }

  def testStaticForwarder_TypeParameterReturnType_AssignableToExactType(): Unit = {
    assertErrorsTextInJava(
      CommonScalaDefinitions,
      """import scala.Product;
        |
        |public class Usage {
        |    User user = null;
        |
        |    void usage1(String[] args) {
        |        Encoder<User> encoder = User.encoder();
        |        User value = User.value();
        |        User current = User.current();
        |        User transform = User.transform(user);
        |        User select = User.select(user);
        |        User.current_$eq(user);
        |    }
        |}
        |""".stripMargin,
      javaClassName = "Usage",
      """Error(value,Incompatible types. Found: 'scala.Product', required: 'User')
        |Error(current,Incompatible types. Found: 'scala.Product', required: 'User')
        |Error(transform,Incompatible types. Found: 'scala.Product', required: 'User')
        |Error(select,Incompatible types. Found: 'scala.Product', required: 'User')""".stripMargin
    )
  }

  def testCompanionModule_GenericReturnType_TypeArgumentPreserved(): Unit = {
    assertNoErrorsInJava(
      CommonScalaDefinitions,
      """public class Usage {
        |    Encoder<User> encoder = User$.MODULE$.encoder();
        |}
        |""".stripMargin,
      javaClassName = "Usage"
    )
  }

  def testCompanionModule_TypeParameterReturnType_AssignableToBound(): Unit = {
    assertNoErrorsInJava(
      CommonScalaDefinitions,
      """import scala.Product;
        |
        |public class Usage {
        |    User user = null;
        |
        |    void usage2(String[] args) {
        |        Encoder<User> encoder = User$.MODULE$.encoder();
        |        Product value = User$.MODULE$.value();
        |        Product current = User$.MODULE$.current();
        |        Product transform = User$.MODULE$.transform(user);
        |        Product select = User$.MODULE$.select(user);
        |        User$.MODULE$.current_$eq(user);
        |    }
        |}
        |""".stripMargin,
      javaClassName = "Usage"
    )
  }

  def testCompanionModule_TypeParameterReturnType_AssignableToExactType(): Unit = {
    assertNoErrorsInJava(
      CommonScalaDefinitions,
      """import scala.Product;
        |
        |public class Usage {
        |    User user = null;
        |
        |    void usage2(String[] args) {
        |        Encoder<User> encoder = User$.MODULE$.encoder();
        |        User value = User$.MODULE$.value();
        |        User current = User$.MODULE$.current();
        |        User transform = User$.MODULE$.transform(user);
        |        User select = User$.MODULE$.select(user);
        |        User$.MODULE$.current_$eq(user);
        |    }
        |}
        |""".stripMargin,
      javaClassName = "Usage",
    )
  }
}
