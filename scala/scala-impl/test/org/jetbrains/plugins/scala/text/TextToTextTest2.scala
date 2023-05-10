package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaReflectLibraryLoader
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Library

class TextToTextTest2 extends TextToTextTestBase {
  override protected def isScala3: Boolean = false

  override protected def libraries: Seq[Library] = Seq(
    Library(
      Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.7.0",
        "com.typesafe.akka" %% "akka-http" % "10.5.0",
        "com.typesafe.akka" %% "akka-stream" % "2.7.0", // Provided dependency of akka-http)
      ),
      Seq("akka"), Seq.empty, 2008,
      Seq(
        "akka.dispatch.CachingConfig",
        "akka.dispatch.ExecutorServiceDelegate",
        "akka.http.impl.engine.rendering.HttpResponseRendererFactory",
        "akka.http.impl.engine.server.HttpServerBluePrint",
        "akka.http.scaladsl.server.Directive",
        "akka.http.scaladsl.server.UnsupportedRequestContentTypeRejection",
        "akka.http.scaladsl.server.directives.BasicDirectives",
        "akka.http.scaladsl.unmarshalling.Unmarshaller",
        "akka.pattern.BackoffSupervisor",
        "akka.stream.Supervision",
        "akka.stream.impl.QueueSource",
        "akka.stream.impl.VirtualProcessor",
        "akka.stream.impl.io.ConnectionSourceStage",
        "akka.stream.impl.io.compression.DeflateDecompressor",
        "akka.stream.scaladsl.MergeHub",
        "akka.stream.stage.GraphStageLogic",
      )
    ),

    Library(
      Seq(
        "org.typelevel" %% "cats-core" % "2.8.0",
        "org.typelevel" %% "cats-effect" % "3.3.14",
      ),
      Seq("cats"), Seq.empty, 1330,
      Seq(
        "cats.arrow.FunctionKMacros",
        "cats.arrow.FunctionKMacroMethods",
      ),
    ),

    Library(
      Seq(
        "co.fs2" %% "fs2-core" % "3.6.1",
      ),
      Seq("fs2"), Seq.empty, 56,
      Seq(
        "fs2.Pull",
      ),
    ),

    Library(
      Seq.empty,
      Seq("scala"), Seq.empty, 984,
      Seq(
        "scala.concurrent.impl.Promise",

        "scala.reflect.api.TypeTags",
        "scala.reflect.internal.Chars",
        "scala.reflect.internal.Definitions",
        "scala.reflect.internal.Kinds",
        "scala.reflect.internal.StdNames",
        "scala.reflect.internal.Symbols",
        "scala.reflect.internal.Types",
        "scala.reflect.internal.tpe.CommonOwners",
        "scala.reflect.internal.tpe.FindMembers",
        "scala.reflect.internal.tpe.TypeMaps",
        "scala.reflect.internal.transform.Transforms",
        "scala.reflect.runtime.ReflectionUtils",

        "scala.util.parsing.combinator.PackratParsers",
      ),
    ),

    Library(
      Seq(
        "org.scalaz" %% "scalaz-core" % "7.3.7",
        "org.scalaz" %% "scalaz-effect" % "7.3.7",
      ),
      Seq("scalaz"), Seq.empty, 1588,
      Seq(
        "scalaz.Endomorphic",
        "scalaz.Foralls",
        "scalaz.FreeFunctions",
        "scalaz.Heap",
        "scalaz.LanApply",
        "scalaz.std.StringInstances",
        "scalaz.syntax.ToApplicativeErrorOps",
        "scalaz.syntax.ToMonadErrorOps",
        "scalaz.syntax.ToMonadTellOps",
      ),
    ),

    Library(
      Seq(
        "dev.zio" %% "zio" % "2.0.2",
        "dev.zio" %% "zio-streams" % "2.0.2",
      ),
      Seq("zio"), Seq.empty, 226,
      Seq.empty,
    ),
  )

  override def librariesLoaders = super.librariesLoaders :+ ScalaReflectLibraryLoader
}
