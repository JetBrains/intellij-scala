package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.SystemProperties
import org.apache.ivy.util.{AbstractMessageLogger, Message, MessageLogger}
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ResolveFailure, ResolvedDependency, Types, UnresolvedDependency}
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.project.template.{PathExt, ScalaSdkDescriptor, _}
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaBundle}

import java.nio.file.{Path, Paths}
import java.util.stream.{Stream => JStream}

private[repository] object IvyDetector extends ScalaSdkDetectorDependencyManagerBase {

  private val Log = Logger.getInstance(this.getClass)

  override def friendlyName: String = ScalaBundle.message("ivy2.cache")

  override protected def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = IvySdkChoice(descriptor)

  override protected def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val homePrefix = Paths.get(SystemProperties.getUserHome)
    val ivyHome    = sys.props.get("sbt.ivy.home").map(Paths.get(_)).orElse(Option(homePrefix / ".ivy2")).get
    val scalaRoot = ivyHome / "cache" / "org.scala-lang"

    if (scalaRoot.exists)
      collectJarFiles(scalaRoot)
    else
      JStream.empty()
  }

  override protected def resolveExtraRequiredJarsScala3(descriptor: ScalaSdkDescriptor)
                                                       (implicit indicator: ProgressIndicator): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] = {
    val scala3Version = descriptor.version match {
      case Some(v) => v
      case None    =>
        return Right(descriptor) // this actually shouldn't be triggered
    }

    val scala3CompilerDependency = DependencyDescription("org.scala-lang", "scala3-compiler_3", scala3Version, isTransitive = true)
    val resolver = new LocalCachesResolver(Log)
    val resolveResult = resolver.resolveSafe(scala3CompilerDependency)

    resolveResult match {
      case Left(value)  =>
        import CompilerClasspathResolveFailure._
        value match {
          case ResolveFailure.UnresolvedDependencies(resolved, unresolved, _) =>
            if (unresolved.forall(isOptionalDependency))
              Right(patchedScala3SdkDescriptor(descriptor, resolved, resolver))
            else
              Left(unresolved.map(_.toString).map(UnresolvedArtifact.apply))
          case ResolveFailure.UnknownProblem(problems) =>
            Left(Seq(UnknownResolveIssue(problems)))
          case ResolveFailure.UnknownException(exception) =>
            Left(Seq(UnknownException(exception)))
        }
      case Right(resolved) =>
        Right(patchedScala3SdkDescriptor(descriptor, resolved, resolver))
    }
  }

  private def patchedScala3SdkDescriptor(
    descriptor: ScalaSdkDescriptor,
    compilerDependencies: Seq[ResolvedDependency],
    resolver: LocalCachesResolver
  ): ScalaSdkDescriptor = {
    val compilerJars = compilerDependencies.map(_.file)

    val scala2LibraryDep = compilerDependencies.find(_.info.artId == "scala-library")
    val scala2LibraryJar = scala2LibraryDep.map(_.file)
    val scala2LibrarySrcJar = scala2LibraryDep.flatMap { libDep =>
      val result = resolver.resolveSingleFromCaches(libDep.info.copy(kind = Types.SRC))
      result.toOption.map(_.file)
    }

    descriptor
      .copy(compilerClasspath = compilerJars)
      .withExtraLibraryFiles(scala2LibraryJar.toSeq)
      .withExtraSourcesFiles(scala2LibrarySrcJar.toSeq)
  }

  private def isOptionalDependency(unresolvedDependency: UnresolvedDependency): Boolean = {
    val dependency = unresolvedDependency.info
    // JLine is a transitive dependency of scala compiler but it isn't actually required to compile Scala code.
    // Thus we make it optional.
    //
    // NOTE: Sometimes if you have some old polluted `.ivy` root folder, Ivy can't resolve JLine from caches due to some parse error:
    // java.text.ParseException: [[Fatal Error] ivy-3.19.0.xml:29:23: The prefix "e" for element "e:sbtTransformHash" is not bound. in ~/.ivy2/cache/org.jline/jline-reader/ivy-3.19.0.xml
    //
    // The issue was fixed in SBT:
    // https://github.com/sbt/sbt/issues/1856
    // https://github.com/sbt/sbt/issues/2042
    // But unfortunately some users can still have old broken cached xml files in jline `xml` or it's parent `xml`.
    // E.g jline 3.19.0 has `org.sonatype.oss` as a parent xml which can have a corrupted `xml` file.
    //
    // To solve the issue user should remove `org.jline` and `org.sonatype.oss` from caches.
    // If this doesn't help, cleaning `.ivy2` root folder should solve the issue.
    dependency.org == "org.jline"
  }

  private class LocalCachesResolver(log: Logger)(implicit indicator: ProgressIndicator) extends DependencyManagerBase {
    override protected val useFileSystemResolversOnly: Boolean = true
    override protected def createLogger: MessageLogger = new DelegateLogger(log)
  }

  private class DelegateLogger(log: Logger)(implicit indicator: ProgressIndicator) extends AbstractMessageLogger {
    override def log(msg: String, level: Int): Unit = {
      log.traceSafe(s"ivy resolve message (${levelStr(level)}) ${msg.stripTrailing()}")
    }

    override def rawlog(msg: String, level: Int): Unit = ()

    override def doProgress(): Unit = indicator.checkCanceled()

    override def doEndProgress(msg: String): Unit = ()
  }

  private def levelStr(level: Int): String =
    level match {
      case Message.MSG_VERBOSE => "verbose"
      case Message.MSG_DEBUG   => "debug"
      case Message.MSG_WARN    => "warn"
      case Message.MSG_ERR     => "error"
      case _                   => "info"
    }
}