package org.jetbrains.plugins.scala

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.SystemProperties
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.{ArtifactDownloadReport, ResolveReport}
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.repository.file.FileRepository
import org.apache.ivy.plugins.repository.jar.JarRepository
import org.apache.ivy.plugins.resolver.{ChainResolver, IBiblioResolver, RepositoryResolver, URLResolver}
import org.apache.ivy.util.{DefaultMessageLogger, MessageLogger}
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription.scalaArtifact
import org.jetbrains.plugins.scala.extensions.IterableOnceExt
import org.jetbrains.plugins.scala.project.template._

import java.io.File
import java.net.URL
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.unused
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object DependencyManager extends DependencyManagerBase

abstract class DependencyManagerBase {
  import DependencyManagerBase._

  protected def useFileSystemResolversOnly: Boolean =
    if (ApplicationManager.getApplication == null) //to be able to use DependencyManagerBase outside IntelliJ App
      false
    else
      RegistryManager.getInstance().is("scala.dependency.manager.use.file.system.resolvers.only")

  protected val artifactBlackList: Set[String] = Set.empty

  private def ignoreArtifact(artifactName: String): Boolean = artifactBlackList.contains(stripScalaVersion(artifactName))

  protected val logLevel: Int = org.apache.ivy.util.Message.MSG_WARN

  protected def resolvers: Seq[Resolver] = defaultResolvers

  private final val defaultResolvers: Seq[Resolver] = Seq(Resolver.MavenCentral)

  private def mkIvyXml(deps: Seq[DependencyDescription]): String = {
    s"""
       |<ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
       |<info organisation="org.jetbrains.plugins.scala" module="ij-scala-tests"/>
       | <configurations>
       |   <conf name="compile"/>
       | </configurations>
       |  <dependencies>${deps.map(mkDependencyXML).mkString("\n")}</dependencies>
       |</ivy-module>
    """.stripMargin
  }

  private def mkDependencyXML(dep: DependencyDescription): String = {
    def exclude(pat: String) = pat.split(":").toSeq match {
      case Seq(excludeOrg)                => s"""<exclude org="$excludeOrg"/>"""
      case Seq(excludeOrg, excludeModule) => s"""<exclude org="$excludeOrg" module="$excludeModule"/>"""
      case _ => ""
    }

    val DependencyDescription(org, artId, version, conf, kind, isTransitive, excludes) = dep

    s"""
       |<dependency org="$org" name="$artId" rev="$version" conf="$conf" transitive="$isTransitive" >
       |  <artifact name="$artId" type="$kind" ext="jar" ${dep.classifier} />
       |  ${excludes.map(exclude).mkString("\n")}
       |</dependency>
     """.stripMargin
  }

  protected def customizeIvySettings(settings: IvySettings): Unit = ()

  protected def progressIndicator: Option[ProgressIndicator] = {
    val indicator = ProgressManager.getInstance().getProgressIndicator
    Option(indicator)
  }

  protected def createLogger: MessageLogger = new DefaultMessageLogger(logLevel)

  private def resolveIvy(deps: Seq[DependencyDescription]): Seq[ResolvedDependency] = {

    def makeIvyResolver(resolver: Resolver): RepositoryResolver = resolver match {
      case MavenResolver(name, root) =>
        val iBiblioResolver = new IBiblioResolver
        iBiblioResolver.setRoot(root)
        iBiblioResolver.setM2compatible(true)
        iBiblioResolver.setName(name)
        iBiblioResolver
      case IvyResolver(name, pattern) =>
        val urlResolver = new URLResolver
        urlResolver.addArtifactPattern(pattern)
        urlResolver.addIvyPattern(pattern)
        urlResolver.setName(name)
        urlResolver
    }

    def isFileSystemResolver(ivyResolver: RepositoryResolver): Boolean = {
      ivyResolver.getRepository match {
        case _: FileRepository | _: JarRepository => true
        case _ => false
      }
    }

    def mkIvySettings(): IvySettings = {
      val ivySettings = new IvySettings
      ivySettings.setDefaultIvyUserDir(ivyHome)

      val useFileSystemOnly = useFileSystemResolversOnly
      val myResolvers = resolvers

      val ivyResolvers = myResolvers.map(makeIvyResolver)
      val ivyResolversFiltered = ivyResolvers.filter { ivyResolver =>
        !useFileSystemOnly || isFileSystemResolver(ivyResolver)
      }

      val ivySettingsUrl = DependencyManagerBase.fileSystemOnlyIvySettingsURL
      ivySettings.load(ivySettingsUrl)

      val mainChainResolver = ivySettings.getResolver("main") match {
        case cr: ChainResolver => cr
        case other =>
          throw new AssertionError(s"'main' chain resolver not found (got: ${if (other == null) null else other.getClass.getName}")
      }
      ivyResolversFiltered.foreach { resolver =>
        mainChainResolver.add(resolver)
        resolver.setSettings(ivySettings)
      }
      ivySettings.configureDefaultVersionMatcher()
      customizeIvySettings(ivySettings)
      ivySettings
    }

    if (deps.isEmpty) return Seq.empty

    val logger = createLogger
    org.apache.ivy.util.Message.setDefaultLogger(logger) // ¯\_(ツ)_/¯ SCL-15168

    val ref = new AtomicReference[ResolveReport]()

    val ivy = new Ivy()
    // Using a raw `java.lang.Thread` because `Ivy` is particularly destructive when it comes to the thread interruption
    // mechanism, ultimately calling `Thread#stop()` if the thread doing the resolution has not responded after 2
    // seconds of waiting. It is not worth doing this to an IDEA platform background thread.
    // This is because `java.io.InputStream#read` (which is used in the implementation of the download code) isn't
    // guaranteed to respond to Java thread interruption.
    val thread = new Thread(() => {
      // ATTENTION: settings should be created before other code SCL-15168
      val settings = mkIvySettings()
      ivy.getLoggerEngine.pushLogger(logger)
      ivy.setSettings(settings)
      ivy.bind()

      val report = usingTempFile("ivy", ".xml") { ivyFile =>
        val ivyXml = mkIvyXml(deps)
        Files.write(Paths.get(ivyFile.toURI), ivyXml.getBytes)
        val resolveOptions = new ResolveOptions()
          .setConfs(Array("compile"))
        ivy.resolve(ivyFile.toURI.toURL, resolveOptions)
      }

      ref.set(report)
    })
    // Do not print rethrown exceptions from the resolving thread, in case of interruption. Ivy already reports errors,
    // including interruption, to the provided Ivy logger instance.
    thread.setUncaughtExceptionHandler((_, _) => ())

    thread.start()

    val indicator = progressIndicator.orNull
    while (ref.get() eq null) {
      // Check cancellation.
      if ((indicator ne null) && indicator.isCanceled) {
        // Interrupt the Ivy instance.
        try ivy.interrupt(thread)
        catch {
          case NonFatal(_) => // Ignore non fatal errors
          case _: InterruptedException => // Ignore possible interruption exceptions from the resolving thread
        } finally {
          indicator.checkCanceled() // Propagate the PCE
        }
      }

      // Sleep for a while.
      try Thread.sleep(300L)
      catch {
        case e: InterruptedException =>
          // This can happen on IDEA exit.
          try ivy.interrupt(thread)
          catch {
            case NonFatal(_) => // Ignore non fatal errors
            case _: InterruptedException => // Ignore possible interruption exceptions from the resolving thread
          } finally {
            throw e // Propagate the InterruptedException
          }
      }
    }

    // The `ref` has already been set, join should be instant here.
    thread.join()
    processIvyReport(ref.get())
  }

  protected def artifactReportToResolvedDependency(artifactReport: ArtifactDownloadReport): ResolvedDependency = {
    val id = artifactReport.getArtifact.getModuleRevisionId
    val file = artifactReport.getLocalFile
    ResolvedDependency(DependencyDescription.fromId(id), file)
  }

  @throws[DependencyManagerBase.ResolveException]
  protected def processIvyReport(report: ResolveReport): Seq[ResolvedDependency] =
    if (report.getAllProblemMessages.isEmpty && report.getAllArtifactsReports.nonEmpty) {
      report
        .getAllArtifactsReports
        .filter(r => !ignoreArtifact(r.getName))
        .map(artifactReportToResolvedDependency)
        .toIndexedSeq
    } else {
      val resolved = report
        .getAllArtifactsReports
        .filter(_.getLocalFile != null)
        .map(artifactReportToResolvedDependency)
        .toSeq
      val unresolved = report.getUnresolvedDependencies.toSeq.map { node =>
        UnresolvedDependency(DependencyDescription.fromId(node.getId))
      }
      throw new ResolveException(resolved, unresolved, report.getAllProblemMessages.asScala.toSeq.filterByType[String])
    }

  /** see [[resolveSafe]] */
  def resolve(dependencies: DependencyDescription*): Seq[ResolvedDependency] = {
    val (unresolved, resolved) = resolveFromCaches(dependencies)
    resolved ++ resolveIvy(unresolved)
  }

  private def resolveFromCaches(dependencies: Seq[DependencyDescription]): (Seq[DependencyDescription], Seq[ResolvedDependency]) = {
    val result: Seq[Either[DependencyDescription, ResolvedDependency]] =
      dependencies.map(resolveSingleFromCaches)
    result.partitionMap(identity)
  }

  def resolveSingleFromCaches(dependency: DependencyDescription): Either[DependencyDescription, ResolvedDependency] =
    dependency match {
      case info@DependencyDescription(org, artId, version, _, kind, false, _) if !ignoreArtifact(artId) =>
        val relativePath = s"cache/$org/$artId/${kind}s/$artId-$version${info.classifierBare.fold("")("-" + _)}.jar"
        val file = new File(ivyHome, relativePath)
        if (file.exists())
          Right(ResolvedDependency(info, file))
        else
          Left(info)
      case info =>
        Left(info)
    }

  /** @see [[resolve]] */
  def resolveSafe(dependencies: DependencyDescription*): Either[ResolveFailure, Seq[ResolvedDependency]] = {
    val (unresolvedLocally, resolvedLocally) = resolveFromCaches(dependencies)

    if (unresolvedLocally.isEmpty)
      Right(resolvedLocally)
    else
      resolveIvySafe(unresolvedLocally)
        .map(deps => resolvedLocally ++ deps)
        .left
        .map {
          case f: ResolveFailure.UnresolvedDependencies =>
            f.copy(resolved = resolvedLocally ++ f.resolved)
          case f => f
        }
  }

  def resolveIvySafe(deps: Seq[DependencyDescription]): Either[ResolveFailure, Seq[ResolvedDependency]] = {
    val result = scala.util.Try {
      resolveIvy(deps)
    }
    result.toEither.left.map {
      case re: ResolveException if re.unresolved.isEmpty =>
        ResolveFailure.UnknownProblem(re.allProblemMessages)
      case re: ResolveException =>
        ResolveFailure.UnresolvedDependencies(re.resolved, re.unresolved, re.allProblemMessages)
      case ex =>
        ResolveFailure.UnknownException(ex)
    }
  }

  def resolveSingle(dependency: DependencyDescription): ResolvedDependency = resolve(dependency).headOption.getOrElse {
    throw new ResolveException(Nil, Seq(UnresolvedDependency(dependency)), Seq(s"Can't resolve single dependency: ${dependency.toString}"))
  }
}

object DependencyManagerBase {

  private val homePrefix: File = sys.props.get("tc.idea.prefix").orElse(Some(SystemProperties.getUserHome)).map(new File(_)).get
  val ivyHome: File = sys.props.get("sbt.ivy.home")
    .orElse(sys.env.get("TC_SBT_IVY_HOME").filter(_ => isUnitTestMode))
    .map(new File(_)).orElse(Option(new File(homePrefix, ".ivy2"))).get

  private val fileSystemOnlyIvySettingsURL: URL = {
    val clazz = classOf[DependencyManagerBase]
    val resourcePath = "dependencyManager/ivysettings-file-system-resolvers-only.xml"
    val url = clazz.getClassLoader.getResource(resourcePath)
    url.ensuring(_ != null, s"Can't locate ivy settings `$resourcePath`")
  }

  object Types extends Enumeration {

    final class Type extends super.Val {
      override def toString: String = super.toString.toLowerCase
    }

    val JAR, SRC = new Type
  }

  import Types._

  case class DependencyDescription(org: String,
                                   artId: String,
                                   version: String,
                                   conf: String = "compile->default(compile)",
                                   kind: Type = JAR,
                                   isTransitive: Boolean = false,
                                   excludes: Seq[String] = Seq.empty) {
    def %(version: String): DependencyDescription = copy(version = version)
    def %(kind: Type): DependencyDescription = copy(kind = kind)
    def sources(): DependencyDescription = copy(kind = Types.SRC)
    def configuration(conf: String): DependencyDescription = copy(conf = conf)
    def transitive(): DependencyDescription = copy(isTransitive = true)
    def exclude(patterns: String*): DependencyDescription = copy(excludes = patterns)
    override def toString: String = {
      val kindHint = if (kind == Types.SRC) " (sources)" else ""
      s"$org:$artId:$version$kindHint"
    }
  }
  object DependencyDescription {
    def fromId(id: ModuleRevisionId): DependencyDescription =
      DependencyDescription(id.getOrganisation, id.getName, id.getRevision)


    /**
     * @param kind compiler / library / reflect / etc...
     */
    def scalaArtifact(kind: String, scalaVersion: ScalaVersion): DependencyDescription = {
      /**
       * Examples:
       *  - https://mvnrepository.com/artifact/org.scala-lang/scala3-library_3/3.0.0
       *  - https://mvnrepository.com/artifact/org.scala-lang/scala-library/2.13.6
       */
      val (org, idPrefix, idSuffix) =
        if (scalaVersion.isScala3)
          ("org.scala-lang", "scala3", "_3")
        else
          ("org.scala-lang", "scala", "")

      DependencyDescription(org, idPrefix + "-" + kind + idSuffix, scalaVersion.minor)
    }
  }

  sealed trait Dependency
  case class UnresolvedDependency(info: DependencyDescription) extends Dependency
  case class ResolvedDependency(info: DependencyDescription, file: File) extends Dependency

  sealed trait Resolver
  object Resolver {
    val MavenCentral: MavenResolver = MavenResolver(
      "central",
      "https://repo1.maven.org/maven2"
    )
    val TypesafeReleases: IvyResolver = IvyResolver(
      "typesafe-releases",
      "https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]"
    )
    @unused("This resolver might be unused now but can be handy to have as a predefined")
    val TypesafeScalaPRValidationSnapshots: MavenResolver = MavenResolver(
      "scala-pr-validation-snapshots",
      "https://scala-ci.typesafe.com/artifactory/scala-pr-validation-snapshots"
    )
  }

  case class MavenResolver(name: String, root: String) extends Resolver

  /** @param pattern same generic pattern for both artifact & ivy files e.g. <br>
   *                 for artifact: https://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt/0.12.4/jars/sbt.jar<br>
   *                 for ivy file: https://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt/0.12.4/ivys/ivy.xml<br>
   */
  case class IvyResolver(name: String, pattern: String) extends Resolver

  def scalaCompilerDescription(implicit scalaVersion: ScalaVersion): DependencyDescription = scalaArtifact("compiler", scalaVersion)
  def scalaLibraryDescription(implicit scalaVersion: ScalaVersion): DependencyDescription = scalaArtifact("library", scalaVersion)
  def scalaReflectDescription(implicit scalaVersion: ScalaVersion): DependencyDescription = scalaArtifact("reflect", scalaVersion)

  implicit class RichStr(private val org: String) extends AnyVal {

    def %(artId: String): DependencyDescription = DependencyDescription(org, artId, "")

    def %%(artId: String)(implicit scalaVersion: ScalaVersion): DependencyDescription = {
      val artifactId =
        if (scalaVersion.isScala3) artId + "_3"
        else artId + "_" + scalaVersion.major
      DependencyDescription(org, artifactId, "")
    }
  }

  implicit class DependencyDescriptionExt(private val dep: DependencyDescription) extends AnyVal {

    def classifier: String = classifierBare.fold("") { bare =>
      "e:classifier=\"" + bare + "\""
    }

    def classifierBare: Option[String] = dep.kind match {
      case Types.SRC => Some("sources")
      case _ => None
    }
  }

  def stripScalaVersion(str: String): String = str.replaceAll("_\\d+\\.\\d+$", "")

  class ResolveException(
    val resolved: Seq[ResolvedDependency],
    val unresolved: Seq[UnresolvedDependency],
    val allProblemMessages: Seq[String],
  ) extends RuntimeException(allProblemMessages.mkString("\n"))

  sealed trait ResolveFailure
  object ResolveFailure {
    final case class UnresolvedDependencies(
      resolved: Seq[ResolvedDependency],
      unresolved: Seq[UnresolvedDependency],
      allProblemMessages: Seq[String],
    ) extends ResolveFailure

    final case class UnknownProblem(allProblemMessages: Seq[String]) extends ResolveFailure
    final case class UnknownException(exception: Throwable) extends ResolveFailure
  }
}
