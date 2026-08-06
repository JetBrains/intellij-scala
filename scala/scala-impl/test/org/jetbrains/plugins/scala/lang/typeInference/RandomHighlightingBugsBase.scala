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
}

@Category(Array(classOf[TypecheckerTests]))
abstract class RandomZioHighlightingBugsBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders :+ IvyManagedLoader(
    "dev.zio" %% "zio" % "2.1.0",
    "dev.zio" %% "zio-mock" % "1.0.0-RC12",
    "dev.zio" %% "zio-http" % "3.0.0-RC7"
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
