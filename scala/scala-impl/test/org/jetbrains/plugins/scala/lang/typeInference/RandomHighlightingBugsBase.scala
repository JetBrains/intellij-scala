package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

final class RandomZioHighlightingBugs_Scala2 extends RandomZioHighlightingBugsBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2
}

final class RandomZioHighlightingBugs_Scala3 extends RandomZioHighlightingBugsBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testSCL22563(): Unit = checkTextHasNoErrors(
    """import zio.mock.Proxy
      |import zio.{IO, URLayer, ZIO, ZLayer}
      |
      |trait Subscriber:
      |  def invoke(event: String): IO[String, Unit]
      |
      |object SubscriberLive:
      |  def create: URLayer[Proxy, Subscriber] =
      |    ZLayer:
      |      for proxy <- ZIO.succeed("test")
      |      yield new Subscriber:
      |        def invoke(event: String) = ???
      |""".stripMargin
  )

  def testSCL22570(): Unit = checkTextHasNoErrors(
    """
      |import zio.http.codec.HttpCodecType.Content
      |import zio.http.codec.{HttpCodec, HttpCodecType}
      |import zio.http.endpoint.{Endpoint, EndpointMiddleware}
      |
      |class C:
      |  val value1: Endpoint[Int, Long, _ <: Nothing, String, EndpointMiddleware] = ???
      |  val value2: HttpCodec[HttpCodecType.Status & Content, String] = ???
      |
      |  //IntelliJ type : Endpoint[Int, Long, Either[String, Error], String, EndpointMiddleware]
      |  //Compiler type : Endpoint[Int, Long, String, String, EndpointMiddleware]
      |  val value3: Endpoint[Int, Long, String, String, EndpointMiddleware] =
      |    value1.outErrors[String].apply(value2, value2)
      |""".stripMargin
  )

  def testSCL22568(): Unit = checkTextHasNoErrors(
    """import zio.*
      |
      |object MainApp extends ZIOAppDefault {
      |  def run =
      |    for
      |      _ <- ZIO.attempt(Some("test")).retryOrElse(
      |        Schedule.fixed(1.minutes) && Schedule.recurs(3),
      |        (error, _) => ZIO.logError("error happend!")
      |      )
      |    yield ()
      |}
      |""".stripMargin
  )
}

@Category(Array(classOf[TypecheckerTests]))
abstract class RandomZioHighlightingBugsBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders :+ IvyManagedLoader(
    "dev.zio" %% "zio" % "2.1.0",
    "dev.zio" %% "zio-mock" % "1.0.0-RC12",
    "dev.zio" %% "zio-http" % "3.0.0-RC7"
  )

  // The type parameter of `ZLayer.scoped[R]` is inferred from the argument of `apply`,
  // with the types inferred by scalac 2.13.14 and by dotc 3.7.4
  def testSCL22614(): Unit = checkSCL22614(error = "Nothing")

  def testSCL22614_Throwable(): Unit = checkSCL22614(error = "Throwable")

  private def checkSCL22614(error: String): Unit = checkTextHasNoErrors(
    s"""import zio.{ZIO, ZLayer}
       |
       |object Example {
       |  private val zio1: ZIO[String, $error, String] = ???
       |  private val zio2: ZIO[String, $error, String] = ???
       |
       |  def value1 = ZLayer.scoped {
       |    for {
       |      _ <- zio1
       |      _ <- zio2
       |    } yield ()
       |  }
       |
       |  def value2 = ZLayer.scoped {
       |    zio1.flatMap(_ => zio2.map(_ => ()))
       |  }
       |
       |  val a: ZLayer[String, $error, Unit] = value1
       |  val b: ZLayer[String, $error, Unit] = value2
       |}
       |""".stripMargin
  )

  //SCL-20982
  def testSCL20982(): Unit = checkTextHasNoErrors(
    """import zio.ZLayer
      |
      |class Dependency
      |
      |class Service(d: Dependency)
      |
      |object Service {
      |  val live: ZLayer[Dependency, Nothing, Service] =
      |    ZLayer.service[Dependency] >>> ZLayer.fromFunction(new Service(_))
      |}
      |""".stripMargin
  )
}
