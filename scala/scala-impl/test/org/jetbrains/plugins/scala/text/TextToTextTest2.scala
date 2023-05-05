package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaReflectLibraryLoader

class TextToTextTest2 extends TextToTextTestBase {
  override protected def isScala3: Boolean = false

  override protected val dependencies = Seq(
    "com.typesafe.akka" %%  "akka-actor" % "2.7.0",
    "com.typesafe.akka" %% "akka-http-core" % "10.5.0",
    "com.typesafe.akka" %% "akka-http" % "10.5.0",
    "com.typesafe.akka" %% "akka-parsing" % "10.5.0",
    "com.typesafe.akka" %% "akka-stream" % "2.7.0",
    "com.typesafe" % "config" % "1.4.2",

    "org.typelevel" %% "cats-core" % "2.8.0",
    "org.typelevel" %% "cats-kernel" % "2.8.0",
    "org.typelevel" %% "cats-effect" % "3.3.14",
    "org.typelevel" %% "cats-effect-kernel" % "3.3.14",
    "org.typelevel" %% "cats-effect-std" % "3.3.14",

    "co.fs2" %% "fs2-core" % "3.6.1",
    "org.scodec" %% "scodec-bits" % "1.1.35",

    "org.scalaz" %% "scalaz-core" % "7.3.7",
    "org.scalaz" %% "scalaz-effect" % "7.3.7",

    "dev.zio" %% "zio" % "2.0.2",
    "dev.zio" %% "zio-streams" % "2.0.2",
  )

  override def librariesLoaders = super.librariesLoaders :+ ScalaReflectLibraryLoader

  override protected val packages = Seq(
    "akka",
    "cats",
    "fs2",
    "scala",
    "scalaz",
    "zio",
  )

  override protected val packageExceptions = Set(
    "akka.stream"
  )

  override protected val classExceptions = Set(
    "akka.actor.Terminated",
    "akka.actor.UnhandledMessage",
    "akka.dispatch.CachingConfig",
    "akka.dispatch.ExecutorServiceDelegate",
    "akka.http.impl.engine.rendering.HttpResponseRendererFactory",
    "akka.http.impl.engine.server.HttpServerBluePrint",
    "akka.http.scaladsl.server.Directive",
    "akka.http.scaladsl.server.UnsupportedRequestContentTypeRejection",
    "akka.http.scaladsl.server.directives.BasicDirectives",
    "akka.http.scaladsl.unmarshalling.Unmarshaller",
    "akka.pattern.BackoffSupervisor",

    "cats.arrow.FunctionKMacros",
    "cats.arrow.FunctionKMacroMethods",

    "fs2.Pull",
    "fs2.concurrent.SignallingMapRef",

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

    "scalaz.Endomorphic",
    "scalaz.Foralls",
    "scalaz.FreeFunctions",
    "scalaz.Heap",
    "scalaz.LanApply",
    "scalaz.std.StringInstances",
    "scalaz.syntax.ToApplicativeErrorOps",
    "scalaz.syntax.ToMonadErrorOps",
    "scalaz.syntax.ToMonadTellOps",

    "zio.VersionSpecific",
  )

  override protected val minClassCount: Int = 5590
}
