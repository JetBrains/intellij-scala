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
}

@Category(Array(classOf[TypecheckerTests]))
abstract class RandomZioHighlightingBugsBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders :+ IvyManagedLoader(
    "dev.zio" %% "zio" % "2.1.0",
    "dev.zio" %% "zio-mock" % "1.0.0-RC12"
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
