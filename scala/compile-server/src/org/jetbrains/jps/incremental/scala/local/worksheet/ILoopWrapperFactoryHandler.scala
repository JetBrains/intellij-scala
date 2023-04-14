package org.jetbrains.jps.incremental.scala.local.worksheet

import org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface.ILoopWrapper
import org.jetbrains.jps.incremental.scala.local.worksheet.util.IOUtils
import org.jetbrains.jps.incremental.scala.{Client, MessageKind, compilerVersion}
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.compiler.data.{CompilerJars, SbtData}
import org.jetbrains.plugins.scala.project.Version
import sbt.util.{Level, Logger}

import java.io.{File, PrintStream}
import java.lang.reflect.InvocationTargetException
import java.net.{URLClassLoader, URLDecoder}

class ILoopWrapperFactoryHandler {
  import ILoopWrapperFactoryHandler._

  private var cachedReplFactory: Option[CachedReplFactory] = None

  def loadReplWrapperAndRun(
    args: WorksheetArgs.RunRepl,
    replContext: ReplContext,
    out: PrintStream, // the consumed output is delegated to client then encoded and delegated to Nailgun out and sent to the
    client: Client
  ): Unit = try {
    val compilerJars = replContext.compilerJars
    val compilerVersionFromProperties  = compilerVersion(compilerJars.compilerJar)
    val scalaVersion = compilerVersionFromProperties.fold(FallBackScalaVersion)(ScalaVersion.apply)
    val replWrapperClassName = wrapperClassNameFor(scalaVersion)

    if (args.dropCachedReplInstance) {
      cachedReplFactory.foreach(_.replFactory.clearSession(args.sessionId))
    }

    // TODO: improve caching, for now we can have only 1 instance with 1 version of scala
    val cachedFactory = cachedReplFactory match {
      case Some(cached@CachedReplFactory(_, _, oldVersion)) if oldVersion == scalaVersion =>
        client.internalDebug("using cached cachedReplFactory")
        cached
      case _ =>
        client.internalDebug("creating new cachedReplFactory")
        val loader = createClassLoader(compilerJars)
        val iLoopWrapper = new ILoopWrapperFactory
        cachedReplFactory.foreach(_.replFactory.clearCaches())
        val cached = CachedReplFactory(loader, iLoopWrapper, scalaVersion)
        cached
    }

    cachedReplFactory = Some(cachedFactory)

    client.progress("Running REPL...")
    IOUtils.patchSystemOut(out)
    val factory = cachedFactory.replFactory
    factory.loadReplWrapperAndRun(args, replContext, out, replWrapperClassName, scalaVersion, client, cachedFactory.classLoader)
  } catch {
    case e: InvocationTargetException =>
      throw e.getTargetException
  }
}

//noinspection TypeAnnotation
object ILoopWrapperFactoryHandler {

  private case class CachedReplFactory(
    classLoader: ClassLoader,
    replFactory: ILoopWrapperFactory,
    scalaVersion: ScalaVersion
  )

  // 2.12 works OK for 2.11 as well
  private final val ILoopWrapper212Impl    = "ILoopWrapper212Impl"
  private final val ILoopWrapper212_13Impl = "ILoopWrapper212_13Impl"
  private final val ILoopWrapper213_0Impl  = "ILoopWrapper213_0Impl"
  private final val ILoopWrapper213Impl    = "ILoopWrapper213Impl"
  private final val ILoopWrapper300Impl    = "ILoopWrapper300Impl"
  private final val ILoopWrapper312Impl    = "ILoopWrapper312Impl"
  private final val ILoopWrapper330Impl    = "ILoopWrapper330Impl"

  private def wrapperClassNameFor(version: ScalaVersion): String = {
    val versionStr = version.value.presentation

    val wrapper = if (versionStr.startsWith("2.13.0")) ILoopWrapper213_0Impl
    else if (versionStr.startsWith("2.13")) ILoopWrapper213Impl
    else if (version.isScala3) {
      //TODO: this is basically equivalent to `3.0.0 <= version < 3.1.2,
      //  reuse org.jetbrains.plugins.scala.project.Version
      if (versionStr.startsWith("3.0") || versionStr.startsWith("3.1.0") || versionStr.startsWith("3.1.1"))
        ILoopWrapper300Impl
      else if (versionStr.startsWith("3.1.2") || versionStr.startsWith("3.1.3") || versionStr.startsWith("3.2"))
        ILoopWrapper312Impl
      else
        ILoopWrapper330Impl
    }
    // note: lexicographic comparison is used, but it should work fine
    else if (version.value >= Version("2.12.13")) ILoopWrapper212_13Impl
    else ILoopWrapper212Impl
    wrapper
  }

  private[worksheet] case class ScalaVersion(value: Version) {
    // temp solution while dotty is evolving very fast
    val isScala3: Boolean = value.presentation.startsWith("3.")
  }

  private[worksheet] object ScalaVersion {
    def apply(versionStr: String): ScalaVersion = ScalaVersion(Version(versionStr))
  }

  private val FallBackScalaVersion = ScalaVersion("2.12.0")

  private def findContainingJar(clazz: Class[_]): Option[File] = {
    val resource = clazz.getResource(s"/${clazz.getName.replace('.', '/')}.class")

    if (resource == null) return None

    val url = URLDecoder.decode(resource.toString.stripPrefix("jar:file:"), "UTF-8")
    val idx = url.indexOf(".jar!")
    if (idx == -1) return None

    Some(new File(url.substring(0, idx + 4))).filter(_.exists())
  }

  private def createClassLoader(compilerJars: CompilerJars): URLClassLoader = {
    val iLoopWrapperClass = classOf[ILoopWrapper]
    val wrapperImplsJar =
      findContainingJar(iLoopWrapperClass)
        .map(_.toPath.getParent.getParent.resolve("worksheet-repl-interface").resolve("impls.jar").toFile)
        .toSeq

    val jars = wrapperImplsJar ++ compilerJars.allJars
    val replInterfaceLoader = classOf[ILoopWrapper].getClassLoader
    new URLClassLoader(sbt.io.Path.toURLs(jars), replInterfaceLoader)
  }

  // use for debugging
  private class ClientDelegatingLogger(client: Client) extends Logger {
    override def trace(t: => Throwable): Unit = client.trace(t)
    override def success(message: => String): Unit = client.info(s"success: $message")
    override def log(level: Level.Value, message: => String): Unit = client.message(toMessageKind(level), message)

    private def toMessageKind(level: Level.Value): MessageKind = level match {
      case sbt.util.Level.Debug => MessageKind.Info
      case sbt.util.Level.Info  => MessageKind.Info
      case sbt.util.Level.Warn  => MessageKind.Warning
      case Level.Error          => MessageKind.Error
    }
  }

  case class ReplContext(sbtData: SbtData,
                         compilerJars: CompilerJars,
                         classpath: Seq[File],
                         scalacOptions: Seq[String])
}