package org.jetbrains.plugins.scala

import java.io.File
import java.nio.file.{Files, Paths}

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.{ChainResolver, IBiblioResolver, RepositoryResolver, URLResolver}
import org.apache.ivy.util.{DefaultMessageLogger, MessageLogger}
import org.jetbrains.plugins.scala.project.template._

import scala.collection.JavaConverters

abstract class DependencyManagerBase {
  import DependencyManagerBase._

  private val homePrefix = sys.props.get("tc.idea.prefix").orElse(sys.props.get("user.home")).map(new File(_)).get
  private val ivyHome = sys.props.get("sbt.ivy.home").map(new File(_)).orElse(Option(new File(homePrefix, ".ivy2"))).get

  protected val artifactBlackList: Set[String] = Set("scala-library", "scala-reflect", "scala-compiler")
  protected val logLevel: Int = org.apache.ivy.util.Message.MSG_WARN

  protected val resolvers: Seq[Resolver] = Seq(
    MavenResolver("central", "https://repo1.maven.org/maven2"),
    MavenResolver("scalaz-releases", "https://dl.bintray.com/scalaz/releases"),
    IvyResolver("typesafe-releases",
      "https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).jar")
  )

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

  protected def createLogger: MessageLogger = new DefaultMessageLogger(logLevel)

  private def resolveIvy(deps: Seq[DependencyDescription]): Seq[ResolvedDependency] = {

    org.apache.ivy.util.Message.setDefaultLogger(createLogger) // ¯\_(ツ)_/¯ SCL-15168

    def mkResolver(resolver: Resolver): RepositoryResolver = resolver match {
      case MavenResolver(name, root) =>
        val iBiblioResolver = new IBiblioResolver
        iBiblioResolver.setRoot(root)
        iBiblioResolver.setM2compatible(true)
        iBiblioResolver.setName(name)
        iBiblioResolver
      case IvyResolver(name, pattern) =>
        val urlResolver = new URLResolver
        urlResolver.addArtifactPattern(pattern)
        urlResolver.setName(name)
        urlResolver
    }

    def mkIvySettings(): IvySettings = {
      val ivySettings = new IvySettings
      val ivyResolvers = resolvers.map(mkResolver)
      ivySettings.setDefaultIvyUserDir(ivyHome)
      ivySettings.load(IvySettings.getDefaultSettingsURL)
      ivySettings.getResolver("main") match {
        case cr: ChainResolver => ivyResolvers.foreach(cr.add)
        case _ =>
          val chainResolver = new ChainResolver
          chainResolver.setName("chainResolver")
          ivyResolvers.foreach(chainResolver.add)
          ivySettings.addResolver(chainResolver)
          ivySettings.setDefaultResolver("chainResolver")
      }
      ivySettings.configureDefaultVersionMatcher()
      ivyResolvers.foreach(_.setSettings(ivySettings))
      customizeIvySettings(ivySettings)
      ivySettings
    }

    if (deps.isEmpty) return Seq.empty

    val settings = mkIvySettings()
    val ivy = new Ivy()
    ivy.getLoggerEngine.pushLogger(createLogger)
    ivy.setSettings(settings)
    ivy.bind()

    val report = usingTempFile("ivy", ".xml") { ivyFile =>
      Files.write(Paths.get(ivyFile.toURI), mkIvyXml(deps).getBytes)
      ivy.resolve(ivyFile.toURI.toURL, new ResolveOptions().setConfs(Array("compile")))
    }

    processIvyReport(report)
  }

  protected def artToDep(id: ModuleRevisionId) = DependencyDescription(id.getOrganisation, id.getName, id.getRevision)

  protected def processIvyReport(report: ResolveReport): Seq[ResolvedDependency] = {

    if (report.getAllProblemMessages.isEmpty && report.getAllArtifactsReports.nonEmpty) {
      report
        .getAllArtifactsReports
        .filter(r => !artifactBlackList.contains(stripScalaVersion(r.getName)))
        .map(a => ResolvedDependency(artToDep(a.getArtifact.getModuleRevisionId), a.getLocalFile))
    } else {
      import JavaConverters._
      throw new RuntimeException(report.getAllProblemMessages.asScala.mkString("\n"))
    }
  }

  def resolve(dependencies: DependencyDescription*): Seq[ResolvedDependency] = {
    val (resolved, unresolved) = dependencies.map {
      case info@DependencyDescription(org, artId, version, _, kind, false, _) =>
        new File(
          ivyHome,
          s"cache/$org/$artId/${kind}s/$artId-$version${info.classifierBare.fold("")("-" + _)}.jar"
        ) match {
          case file if file.exists() => Right(ResolvedDependency(info, file))
          case _ => Left(info)
        }
      case info => Left(info)
    }.partition(_.isRight)

    resolved.map(_.right.get) ++ resolveIvy(unresolved.map(_.left.get))
  }

  def resolveSingle(dependency: DependencyDescription): ResolvedDependency = resolve(dependency).head
}

object DependencyManagerBase {

  object Types extends Enumeration {

    final class Type extends super.Val {
      override def toString: String = super.toString.toLowerCase
    }

    val JAR, BUNDLE, SRC = new Type
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
    def configuration(conf: String): DependencyDescription = copy(conf = conf)
    def transitive(): DependencyDescription = copy(isTransitive = true)
    def exclude(patterns: String*): DependencyDescription = copy(excludes = patterns)
    override def toString: String = s"$org:$artId:$version"
  }

  sealed trait Dependency
  case class UnresolvedDependency(info: DependencyDescription) extends Dependency
  case class ResolvedDependency(info: DependencyDescription, file: File) extends Dependency

  sealed trait Resolver
  case class MavenResolver(name: String, root: String) extends Resolver
  case class IvyResolver(name: String, pattern: String) extends Resolver

  private def scalaDependency(kind: String)
                             (implicit scalaVersion: ScalaVersion) = DependencyDescription(
    "org.scala-lang",
    "scala-" + kind,
    scalaVersion.minor
  )

  def scalaCompilerDescription(implicit scalaVersion: ScalaVersion): DependencyDescription = scalaDependency("compiler")

  def scalaLibraryDescription(implicit scalaVersion: ScalaVersion): DependencyDescription = scalaDependency("library")

  def scalaReflectDescription(implicit scalaVersion: ScalaVersion): DependencyDescription = scalaDependency("reflect")

  implicit class RichStr(private val org: String) extends AnyVal {

    def %(artId: String) = DependencyDescription(org, artId, "")

    def %%(artId: String)(implicit scalaVersion: ScalaVersion) = DependencyDescription(
      org,
      artId + "_" + scalaVersion.major,
      ""
    )
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
}

object DependencyManager extends DependencyManagerBase
