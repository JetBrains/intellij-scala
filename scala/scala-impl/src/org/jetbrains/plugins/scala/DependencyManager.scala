package org.jetbrains.plugins.scala

import java.io.File
import java.nio.file.{Files, Paths}

import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.{ChainResolver, IBiblioResolver, RepositoryResolver, URLResolver}
import org.apache.ivy.util.DefaultMessageLogger
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.project.template._

import scala.collection.JavaConverters.asScalaBufferConverter

abstract class DependencyManagerBase {
  import DependencyManagerBase._

  private val homePrefix = sys.props.get("tc.idea.prefix").orElse(sys.props.get("user.home")).map(new File(_)).get
  private val ivyHome = sys.props.get("sbt.ivy.home").map(new File(_)).orElse(Option(new File(homePrefix, ".ivy2"))).get

  protected val artifactBlackList = Set("scala-library", "scala-reflect", "scala-compiler")
  protected val logLevel: Int = org.apache.ivy.util.Message.MSG_WARN

  protected val resolvers: Seq[Resolver] = Seq(
    MavenResolver("central", "http://repo1.maven.org/maven2"),
    MavenResolver("scalaz-releases", "http://dl.bintray.com/scalaz/releases"),
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

  private def resolveIvy(deps: Seq[DependencyDescription]): Seq[ResolvedDependency] = {

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
      val chainResolver = new ChainResolver
      chainResolver.setName("default")
      resolvers.foreach { r => chainResolver.add(mkResolver(r)) }

      val ivySettings = new IvySettings
      ivySettings.addResolver(chainResolver)
      ivySettings.setDefaultResolver("default")
      ivySettings.setDefaultIvyUserDir(ivyHome)
      ivySettings
    }

    if (deps.isEmpty) return Seq.empty

    val ivy = Ivy.newInstance(mkIvySettings())
    ivy.getLoggerEngine.pushLogger(new DefaultMessageLogger(logLevel))

    val report = usingTempFile("ivy", Some(".xml")) { ivyFile =>
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
    }
    else throw new RuntimeException(report.getAllProblemMessages.asScala.mkString("\n"))
  }

  private def resolveFast(dep: DependencyDescription): Dependency = {
    val DependencyDescription(org, artId, version, _, kind, _, _) = dep
    val suffix = if (dep.classifierBare.nonEmpty) s"-${dep.classifierBare}" else ""
    val file   = new File(ivyHome, s"cache/$org/$artId/${kind}s/$artId-$version$suffix.jar")
    if (!dep.isTransitive && file.exists())
      ResolvedDependency(dep, file)
    else
      UnresolvedDependency(dep)
  }

  def resolve(dependencies: DependencyDescription*): Seq[ResolvedDependency] = {
    val res = dependencies.map(resolveFast)
    val resolvedLocally = res.collect({case d:ResolvedDependency => d})
    val unresolved      = res.collect({case UnresolvedDependency(info) => info})
    resolvedLocally ++ resolveIvy(unresolved)
  }

  def resolveSingle(dependency: DependencyDescription): ResolvedDependency = resolve(dependency).head
}

object DependencyManagerBase {

  object Types extends Enumeration {
    type Type = Value
    val JAR, BUNDLE, SRC = Value
  }

  case class DependencyDescription(org: String,
                                   artId: String,
                                   version: String,
                                   conf: String = "compile->default(compile)",
                                   kind: String = Types.JAR.str,
                                   isTransitive: Boolean = false,
                                   excludes: Seq[String] = Seq.empty) {
    def %(version: String): DependencyDescription = copy(version = version)
    def %(kind: Types.Type): DependencyDescription = copy(kind = kind.str)
    def configuration(conf: String): DependencyDescription = copy(conf = conf)
    def transitive(): DependencyDescription = copy(isTransitive = true)
    def exclude(patterns: String*): DependencyDescription = copy(excludes = patterns)
    override def toString: String = s"$org:$artId:$version"
  }

  sealed trait Dependency
  case class UnresolvedDependency(info: DependencyDescription) extends Dependency
  case class ResolvedDependency(info: DependencyDescription, file: File) extends Dependency {
    def toJarVFile: VirtualFile = JarFileSystem.getInstance().refreshAndFindFileByPath(s"${file.getCanonicalPath}!/")
  }

  sealed trait Resolver
  case class MavenResolver(name: String, root: String) extends Resolver
  case class IvyResolver(name: String, pattern: String) extends Resolver

  implicit class RichStr(value: String) {
    def %(right: String) = DependencyDescription(value, right, "")
    def %%(right: String)(implicit scalaVersion: ScalaVersion): DependencyDescription =
      DependencyDescription(value, s"${right}_${scalaVersion.major}", "")
  }

  implicit class DependencyDescriptionExt(dep: DependencyDescription) {
    def classifier: String = if (classifierBare.nonEmpty) s"""e:classifier="$classifierBare"""" else ""
    def classifierBare: String = if (dep.kind == Types.SRC.str) "sources" else ""
  }

  implicit class TypesExt(tp: Types.Type) { def str: String = tp.toString.toLowerCase }

  def stripScalaVersion(str: String): String = str.replaceAll("_\\d+\\.\\d+$", "")
}

object DependencyManager extends DependencyManagerBase