package org.jetbrains.plugins.scala

import java.io.File
import java.nio.file.{Files, Paths}

import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.{ChainResolver, IBiblioResolver, RepositoryResolver, URLResolver}
import org.jetbrains.plugins.scala.DependencyManager.Dependency
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.junit.Assert

import scala.collection.JavaConverters.asScalaBufferConverter

class DependencyManager(val deps: Dependency*) {
  import DependencyManager._

  private val homePrefix = sys.props.get("tc.idea.prefix").orElse(sys.props.get("user.home")).map(new File(_)).get
  private val ivyHome = sys.props.get("sbt.ivy.home").map(new File(_)).orElse(Option(new File(homePrefix, ".ivy2"))).get
  protected val artifactBlackList = Set("scala-library", "scala-reflect", "scala-compiler")

  protected var resolvers = Seq(
    Resolver("central", "http://repo1.maven.org/maven2/[organisation]/[module]/[revision]/[artifact](-[revision]).jar"),
    Resolver("scalaz-releases", "http://dl.bintray.com/scalaz/releases/[organisation]/[module]/[revision]/[artifact](-[revision]).jar"),
    Resolver("typesafe-releases", "https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).jar", mavenStyle = false)
  )

  private def mkIvyXml(deps: Seq[Dependency]): String = {
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

  private def mkDependencyXML(dep: Dependency): String = {
    def exclude(pat: String) = pat.split(":").toSeq match {
      case Seq(org)       => s"""<exclude org="$org"/>"""
      case Seq(org, name) => s"""<exclude org="$org" module="$name"/>"""
      case _ => ""
    }

    s"""
       |<dependency org="${dep.org}" name="${dep.artId}" rev="${dep.version}" conf="${dep.conf}" transitive="${dep._transitive}" >
       |  <artifact name="${dep.artId}" type="${dep.kind}" ext="jar" ${dep.classifier} />
       |  ${dep.excludes.map(exclude).mkString("\n")}
       |</dependency>
     """.stripMargin
  }

  private def resolveIvy(deps: Seq[Dependency]): Seq[ResolvedDependency] = {
    def artToDep(id: ModuleRevisionId) = Dependency(id.getOrganisation, id.getName, id.getRevision)
    def mkResolver(r: Resolver): RepositoryResolver = {
      var resolver: RepositoryResolver = null
      if (r.mavenStyle) {
        resolver = new IBiblioResolver
        resolver.setM2compatible(true)
      } else {
        resolver = new URLResolver
        resolver.addArtifactPattern(r.pattern)
      }
      resolver.setName(r.name)
      resolver
    }

    if (deps.isEmpty) return Seq.empty

    val ivySettings: IvySettings = new IvySettings
    val chainResolver = new ChainResolver
    chainResolver.setName("default")
    resolvers.foreach { r => chainResolver.add(mkResolver(r)) }
    ivySettings.addResolver(chainResolver)
    ivySettings.setDefaultResolver("default")
    ivySettings.setDefaultIvyUserDir(ivyHome)
    val ivy: Ivy = Ivy.newInstance(ivySettings)
    val ivyFile = File.createTempFile("ivy", ".xml")
    ivyFile.deleteOnExit()
    Files.write(Paths.get(ivyFile.toURI), mkIvyXml(deps).getBytes)
    val resolveOptions = new ResolveOptions().setConfs(Array("compile"))
    val report = ivy.resolve(ivyFile.toURI.toURL, resolveOptions)
    ivyFile.delete()
    if (report.getAllProblemMessages.isEmpty && report.getAllArtifactsReports.length > 0) {
      report
        .getAllArtifactsReports
        .filter(r => !artifactBlackList.contains(stripScalaVersion(r.getName)))
        .map(a => ResolvedDependency(artToDep(a.getArtifact.getModuleRevisionId), a.getLocalFile))
    }
    else {
      Assert.fail(s"${report.getAllProblemMessages.asScala.mkString("\n")}")
      Seq.empty
    }
  }



  private def resolveFast(dep: Dependency): Either[ResolvedDependency, Dependency] = {
    val suffix = if (dep.classifierBare.nonEmpty) s"-${dep.classifierBare}" else ""
    val file = new File(ivyHome, s"cache/${dep.org}/${dep.artId}/${dep.kind}s/${dep.artId}-${dep.version}$suffix.jar")
    if (!dep._transitive && file.exists())
      Left(ResolvedDependency(dep, file))
    else
      Right(dep)
  }

  def resolve(dependencies: Dependency*): Seq[ResolvedDependency] = {
    val (localResolved, unresolved) = partitionEithers(dependencies.map(resolveFast))
    localResolved ++ resolveIvy(unresolved)
  }

  def withResolvers(_resolvers: Seq[Resolver]): DependencyManager = {
    resolvers = resolvers ++ _resolvers
    this
  }
}

object DependencyManager {

  private def stripScalaVersion(str: String): String = str.replaceAll("_\\d+\\.\\d+$", "")

  def apply(deps: Dependency*): DependencyManager = new DependencyManager(deps:_*)

  object Types extends Enumeration {
    type Type = Value
    val JAR, BUNDLE, SRC = Value
  }

  case class Dependency(org: String,
                        artId: String,
                        version: String,
                        conf: String = "compile->default(compile)",
                        _kind: Types.Type = Types.JAR,
                        _transitive: Boolean = false,
                        excludes: Seq[String] = Seq.empty)
  {
    def kind: String = _kind.toString.toLowerCase
    def %(version: String): Dependency = copy(version = version)
    def ^(conf: String): Dependency = copy(conf = conf)
    def %(kind: Types.Type): Dependency = copy(_kind = kind)
    def transitive(): Dependency = copy(_transitive = true)
    def classifier: String = if (classifierBare.nonEmpty) s"""e:classifier="$classifierBare"""" else ""
    def classifierBare: String = if (_kind == Types.SRC) "sources" else ""
    def exclude(patterns: String*): Dependency = copy(excludes = patterns)
    override def toString: String = s"$org:$artId:$version"
  }

  case class ResolvedDependency(info: Dependency, file: File) {
    def toJarVFile: VirtualFile = JarFileSystem.getInstance().refreshAndFindFileByPath(s"${file.getCanonicalPath}!/")
  }

  implicit class RichStr(value: String) {
    def %(right: String) = Dependency(value, right, "UNKNOWN")
    def %%(right: String)(implicit scalaVersion: ScalaVersion): Dependency =
      Dependency(value, s"${right}_${scalaVersion.major}", "UNKNOWN")
  }

  case class Resolver(name: String, pattern: String, mavenStyle: Boolean = true)

  def partitionEithers[A, B](input: Traversable[Either[A, B]]): (IndexedSeq[A], IndexedSeq[B])= {
    val a = IndexedSeq.newBuilder[A]
    a.sizeHint(input)
    val b = IndexedSeq.newBuilder[B]
    b.sizeHint(input)

    for (x <- input) {
      x match {
        case Left(aItem) => a += aItem
        case Right(bItem) => b += bItem
      }
    }

    (a.result(), b.result())
  }
}

