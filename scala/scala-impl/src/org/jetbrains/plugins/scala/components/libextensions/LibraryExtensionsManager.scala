package org.jetbrains.plugins.scala.components.libextensions

import java.io.File

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, IvyResolver, MavenResolver, ResolvedDependency}
import org.jetbrains.plugins.scala.extensions.using
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.{immutable, mutable}
import scala.util.{Failure, Success, Try}
import scala.xml._

class LibraryExtensionsManager(project: Project) extends AbstractProjectComponent(project) {
  import LibraryExtensionsManager._

  class ExtensionNotRegisteredException(iface: Class[_]) extends Exception(s"No extensions registered for class $iface")

  private val LOG = Logger.getInstance(classOf[LibraryExtensionsManager])
  private var myExtensions  = mutable.HashMap[String, ExtensionDescriptor]()
  private var myClassLoaders= immutable.HashMap[LibraryDescriptor, UrlClassLoader]

  override def projectOpened(): Unit = loadCachedExtensions()

  override def projectClosed(): Unit = saveCachedExtensions()

  def searchExtensions(sbtResolvers: Set[SbtResolver]): Unit = {
    val allLibraries = ProjectLibraryTable.getInstance(project).getLibraries
    val ivyResolvers = sbtResolvers.toSeq.collect {
      case r: SbtMavenResolver => MavenResolver(r.name, r.root)
      case r: SbtIvyResolver if r.name != "Local cache" => IvyResolver(r.name, r.root)
    }
    val candidates = getExtensionLibCandidates(allLibraries)
    val resolved = new IvyExtensionsResolver(ivyResolvers.reverse).resolve(candidates.toSeq:_*)
    resolved.foreach(processResolvedExtension)
  }

  private def getExtensionLibCandidates(libs: Seq[Library]): Set[DependencyDescription] = {
    val patterns = ScalaProjectSettings.getInstance(project).getLextSearchPatterns.asScala

    def processLibrary(lib: Library): Seq[DependencyDescription] = lib.getName.split(": ?") match {
      case Array("sbt", org, module, version, "jar") =>
        val subst = patterns.map(_.replace(PAT_ORG, org).replace(PAT_MOD, module).replace(PAT_VER, version))
        subst.map(_.split(s" *$PAT_SEP *")).collect {
          case Array(newOrg,newMod,newVer) => DependencyDescription(newOrg, newMod, newVer)
        }.toSeq
      case _ => Seq.empty
    }

    var resultSet = immutable.HashSet[DependencyDescription]()
    libs.foreach(resultSet ++= processLibrary(_))
    resultSet
  }

  private def processResolvedExtension(resolved: ResolvedDependency): Unit = {
    val file = resolved.toJarVFile
    val manifest = Option(file.findFileByRelativePath(manifestPath))
      .map(vFile => Try(using(vFile.getInputStream)(XML.load)))

    manifest match {
      case Some(Success(xml))       => loadJarWithManifest(xml, resolved.file)
      case Some(Failure(exception)) => LOG.error("Error parsing extensions manifest", exception)
      case None                     => LOG.error(s"No manifest in extensions jar ${resolved.file}")
    }
  }

  private def loadJarWithManifest(manifest: Elem, jarFile: File): Unit = {
    LibraryDescriptor.parse(manifest) match {
      case Left(error)        => LOG.error(s"Failed to parse descriptor: $error")
      case Right(descriptor)  => loadDescriptor(descriptor)
    }
  }

  private def loadDescriptor(descriptor: LibraryDescriptor): Unit = {
    descriptor.getCurrentPluginDescriptor match {
      case Some(IdeaVersionDescriptor(_, _, pluginId, defaultExtensionNs, extensions)) =>
        extensions.foreach { e => ??? }
      case None =>
    }
  }

  private def loadCachedExtensions(): Unit = {
    ???
  }

  private def saveCachedExtensions(): Unit = {
    ???
  }

  def getExtensions[T](iface: Class[T]): Seq[T] = {
    ???
  }

  def getAvailableLibraries: Seq[LibraryDescriptor] = ???

}

object LibraryExtensionsManager {

  val PAT_ORG = "$ORG"
  val PAT_MOD = "$MOD"
  val PAT_VER = "$VER"
  val PAT_SEP = "%"
  val DEFAULT_PATTERN = s"$PAT_ORG $PAT_SEP $PAT_MOD-ijext $PAT_SEP $PAT_VER+"

  val manifestPath = "META-INF/intellij-compat.xml"

  def getInstance(project: Project): LibraryExtensionsManager = project.getComponent(classOf[LibraryExtensionsManager])

}
