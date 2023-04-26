package org.jetbrains.plugins.scala.text

import com.intellij.psi.PsiPackage
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, ScalaReflectLibraryLoader}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert

// SCL-21078
class TextToTextTest extends ScalaFixtureTestCase {
  private val Dependencies = Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.7.0",
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
    "org.scala-lang" % "scala-reflect" % "2.13.10"
  )

  private val Packages = Seq(
    "akka",
    "cats",
    "fs2",
    "scala",
    "scalaz",
    "zio",
  )

  private val PackageExceptions = Set(
    "akka.stream"
  )

  private val ClassExceptions = Set(
    "akka.actor.SupervisorStrategy",
    "akka.actor.Terminated",
    "akka.actor.TypedActor",
    "akka.actor.UnhandledMessage",
    "akka.dispatch.CachingConfig",
    "akka.dispatch.ExecutorServiceDelegate",
    "akka.event.Logging",
    "akka.event.NoLogging",
    "akka.event.NoMarkerLogging",
    "akka.http.impl.engine.rendering.HttpResponseRendererFactory",
    "akka.http.impl.engine.server.HttpServerBluePrint",
    "akka.http.impl.util.DefaultNoLogging",
    "akka.http.scaladsl.Http",
    "akka.http.scaladsl.model.MediaType",
    "akka.http.scaladsl.model.Uri",
    "akka.http.scaladsl.server.Directive",
    "akka.http.scaladsl.server.PathMatcher",
    "akka.http.scaladsl.server.UnsupportedRequestContentTypeRejection",
    "akka.http.scaladsl.server.directives.BasicDirectives",
    "akka.http.scaladsl.unmarshalling.Unmarshaller",
    "akka.io.Tcp",
    "akka.io.Udp",
    "akka.io.UdpConnected",
    "akka.pattern.BackoffSupervisor",
    "akka.serialization.SerializationExtension",
    "akka.stream.",

    "cats.arrow.FunctionKMacros",
    "cats.arrow.FunctionKMacroMethods",

    "fs2.Pull",
    "fs2.concurrent.SignallingMapRef",
    "fs2.internal.AcquireAfterScopeClosed",

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
    "scalaz.Ordering",
    "scalaz.LanApply",
    "scalaz.std.StringInstances",
    "scalaz.syntax.ToApplicativeErrorOps",
    "scalaz.syntax.ToMonadErrorOps",
    "scalaz.syntax.ToMonadTellOps",

    "zio.VersionSpecific",
  )

  override protected def supportedIn(version: ScalaVersion) =
    version >= LatestScalaVersions.Scala_2_13

  override def librariesLoaders =
    super.librariesLoaders :+ ScalaReflectLibraryLoader :+ IvyManagedLoader(Dependencies: _*)

  def testTextToText(): Unit = {
    try {
      ScalaApplicationSettings.PRECISE_TEXT = true
      doTestTextToText()
    } finally {
      ScalaApplicationSettings.PRECISE_TEXT = false
    }
  }

  private def doTestTextToText(): Unit = {
    val manager = ScalaPsiManager.instance(getProject)

    println("Collecting classes...")

    val classes = Packages
      .map(name => manager.getCachedPackage(name).getOrElse(throw new AssertionError(name)))
      .flatMap(pkg => classesIn(pkg, PackageExceptions))

    val total = classes.length

    Assert.assertTrue(total.toString, total > 5500)

    println(s"Testing $total classes:")

    classes.zipWithIndex.foreach { case (cls, i) =>
      println(f"$i%04d/$total%s: ${cls.qualifiedName}")

      val expected = {
        val s1 = cls.getContainingFile.getText
        // TODO Function type by-name parameters, SCL-21149
        val s2 = if (cls.qualifiedName.startsWith("scalaz.")) s1.replace("(=> ", "(").replace(", => ", ", ").replaceAll("\\((\\S+)\\) => ", "$1 => ") else s1
        s2.replaceAll("\\.super\\[.*?\\*/\\]\\.", ".this.")
      }

      val actual = textOfCompilationUnit(cls)

      if (!ClassExceptions(cls.qualifiedName)) {
        Assert.assertEquals(cls.qualifiedName, expected, actual)
      } else {
        Assert.assertFalse(cls.qualifiedName, expected == actual)
      }
    }

    println("Done.")
  }

  private def classesIn(pkg: PsiPackage, exceptions: Set[String]): Seq[ScTypeDefinition] = {
    val packageClasses = pkg.getClasses
      .collect({case c: ScTypeDefinition if c.isInCompiledFile && !(c.isInstanceOf[ScObject] && c.baseCompanion.isDefined) => c})
      .sortBy(_.qualifiedName)

    val subpackageClasses = pkg.getSubPackages
      .filter(pkg => !exceptions(pkg.getQualifiedName))
      .sortBy(_.getQualifiedName)
      .flatMap(classesIn(_, exceptions))

    packageClasses.toSeq ++ subpackageClasses.toSeq
  }

  private def textOfCompilationUnit(cls: ScTypeDefinition): String = {
    val sb = new StringBuilder()

    sb ++= "package " + cls.qualifiedName.substring(0, cls.qualifiedName.lastIndexOf('.')) + "\n"

    ClassPrinter.printTo(sb, cls)
    cls.baseCompanion.foreach { obj =>
      ClassPrinter.printTo(sb, obj)
    }

    sb.setLength(sb.length - 1)

    sb.toString
  }
}
