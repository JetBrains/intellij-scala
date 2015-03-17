package org.jetbrains.sbt
package project.structure

import java.io.File

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.structure.FS._
import org.jetbrains.sbt.project.structure.Play2Keys.{KeyTransformer, KeyExtractor}

import scala.xml.Node

/**
 * @author Pavel Fatin
 */

object StructureParser {
  def parse(node: Node, home: File): Structure = {
    implicit val fs = new FS(home)

    val project = (node \ "project").map(parseProject)
    val repository = (node \ "repository").headOption.map(parseRepository)
    val localCachePath = (node \ "localCachePath").headOption.map(_.text)
    val sbtVersion = (node \ "@sbt").text

    Structure(project, repository, localCachePath, sbtVersion)
  }

  private def parseProject(node: Node)(implicit fs: FS): Project = {
    val id = (node \ "id").text
    val name = (node \ "name").text
    val organization = (node \ "organization").text
    val version = (node \ "version").text
    val base = new File((node \ "base").text)
    val target = file((node \ "target").text)(fs.withBase(base))
    val build = parseBuild(node ! "build")(fs.withBase(base))
    val configurations = (node \ "configuration").map(parseConfiguration(_)(fs.withBase(base)))
    val java = (node \ "java").headOption.map(parseJava(_)(fs.withBase(base)))
    val scala = (node \ "scala").headOption.map(parseScala(_)(fs.withBase(base)))
    val android = (node \ "android").headOption.map(parseAndroid(_)(fs.withBase(base)))
    val dependencies = parseDependencies(node)(fs.withBase(base))
    val resolvers = parseResolvers(node)
    val play2 = (node \ "playimps").headOption.map(parsePlay2(_)(fs.withBase(base)))

    Project(id, name, organization, version, base, target, build, configurations, java, scala,
      android, dependencies, resolvers, play2)
  }

  private def parseBuild(node: Node)(implicit fs: FS): Build = {
    val imports = (node \ "import").map(_.text)
    val classes = (node \ "classes").map(e => file(e.text))
    val docs = (node \ "docs").map(e => file(e.text))
    val sources = (node \ "sources").map(e => file(e.text))

    Build(imports, classes, docs, sources)
  }

  private def parseJava(node: Node)(implicit fs: FS): Java = {
    val home = (node \ "home").headOption.map(e => file(e.text))
    val options = (node \ "option").map(_.text)

    Java(home, options)
  }

  private def parseScala(node: Node)(implicit fs: FS): Scala = {
    val version = (node \ "version").text
    val library = file((node \ "library").text)
    val compiler = file((node \ "compiler").text)
    val extra = (node \ "extra").map(e => file(e.text))
    val options = (node \ "option").map(_.text)

    Scala(Version(version), library, compiler, extra, options)
  }

  def parsePlay2(node: Node)(implicit fs: FS): Play2 = Play2(KeyTransformer.transform(node.child flatMap KeyExtractor.extract))

  private def parseAndroid(node: Node)(implicit fs: FS): Android = {
    val version = (node \ "version").text
    val manifestFile = file((node \ "manifest").text)
    val apkPath = file((node \ "apk").text)
    val resPath = file((node \ "resources").text)
    val assetsPath = file((node \ "assets").text)
    val genPath = file((node \ "generatedFiles").text)
    val libsPath = file((node \ "nativeLibs").text)
    val isLibrary = (node \ "isLibrary").text.toBoolean
    val proguardConfig = (node \ "proguard" \ "option").map(_.text)

    Android(version, manifestFile, apkPath, resPath, assetsPath, genPath, libsPath, isLibrary, proguardConfig)
  }

  private def parseConfiguration(node: Node)(implicit fs: FS): Configuration = {
    val id = (node \ "@id").text
    val sources = (node \ "sources").map(parseDirectory)
    val resources = (node \ "resources").map(parseDirectory)
    val excludes = (node \ "exclude").map(e => file(e.text))
    val classes = file((node ! "classes").text)

    Configuration(id, sources, resources, excludes, classes)
  }

  private def parseDirectory(node: Node)(implicit fs: FS): Directory = {
    val managed = (node \ "@managed").headOption.exists(_.text.toBoolean)
    Directory(file(node.text), managed)
  }

  private def parseDependencies(node: Node)(implicit fs: FS): Dependencies = {
    val projects = (node \ "project").map(parseProjectDependency)
    val modules = (node \ "module").map(parseModuleDependency)
    val jars = (node \ "jar").map(parseJarDependency)

    Dependencies(projects, modules, jars)
  }

  private def parseProjectDependency(node: Node): ProjectDependency = {
    val project = node.text
    val configurations = (node \ "@configurations").headOption
            .map(it => parseConfigurations(it.text)).getOrElse(Seq.empty)

    ProjectDependency(project, configurations)
  }

  private def parseModuleDependency(node: Node): ModuleDependency = {
    val id = parseModuleIdentifier(node)
    val configurations = (node \ "@configurations").headOption
            .map(it => parseConfigurations(it.text)).getOrElse(Seq.empty)

    ModuleDependency(id, configurations)
  }

  private def parseJarDependency(node: Node)(implicit fs: FS): JarDependency = {
    val jar = file(node.text.trim)
    val configurations = (node \ "@configurations").headOption
            .map(it => parseConfigurations(it.text)).getOrElse(Seq.empty)

    JarDependency(jar, configurations)
  }

  private def parseConfigurations(s: String) = if (s.isEmpty) Seq.empty else s.split(";").toSeq

  private def parseModuleIdentifier(node: Node): ModuleId = {
    val organization = (node \ "@organization").text
    val name = (node \ "@name").text
    val revision = (node \ "@revision").text
    val artifactType = (node \ "@artifactType").text
    val classifier = (node \ "@classifier").text

    ModuleId(organization, name, revision, artifactType, if (classifier.isEmpty) None else Some(classifier))
  }

  private def parseRepository(node: Node)(implicit fs: FS): Repository = {
    val modules = (node \ "module").map { it =>
      val identifier = parseModuleIdentifier(it)

      val binaries = (it \ "jar").map(e => file(e.text))
      val docs = (it \ "doc").map(e => file(e.text))
      val sources = (it \ "src").map(e => file(e.text))

      Module(identifier, binaries, docs, sources)
    }

    Repository(new File("."), modules)
  }

  private def parseResolvers(node: Node) =
    (node \ "resolver").map({ r =>
      val name = (r \ "@name").text
      val root = (r \ "@root").text
      Resolver(name, if (root.endsWith("/")) root else root + "/")
    }).toSet

  private implicit class NodeExt(node: Node) {
    def !(name: String): Node = (node \ name) match {
      case Seq() => throw new RuntimeException(s"No $name node in $node")
      case Seq(child) => child
      case _ => throw new RuntimeException(s"Multiple $name nodes in $node")
    }
  }
}

private case class FS(home: File, base: Option[File] = None) {
  def withBase(base: File): FS = copy(base = Some(base))
}

private object FS {
  private val HomePrefix = "~"
  private val BasePrefix = "."
  private val Dots = ".."

  def file(path: String)(implicit fs: FS): File = {
    if (path.startsWith(HomePrefix + "/") || path == HomePrefix) {
      new File(fs.home, path.substring(HomePrefix.length))
    } else if (path.startsWith(BasePrefix + "/") || path == BasePrefix) {
      val parent = fs.base.getOrElse(
        throw new IllegalArgumentException(SbtBundle("sbt.import.noBaseDirForRelativePath", path)))
      new File(parent, path.substring(BasePrefix.length))
    } else if (path.startsWith(Dots + "/") || path == Dots) {
      var path0 = path
      var parent = fs.base.getOrElse(null.asInstanceOf[File])
      while (path0.startsWith(Dots) && parent != null) {
        parent = parent.getParentFile
        path0 = path0.substring(Dots.length)
        path0 = if (path0.headOption == Some('/')) path0.tail else path0
      }
      if (parent == null)
        throw new IllegalArgumentException(SbtBundle("sbt.import.noBaseDirForRelativePath", path))
      new File(parent, path0)
    } else {
      new File(path)
    }
  }
}
