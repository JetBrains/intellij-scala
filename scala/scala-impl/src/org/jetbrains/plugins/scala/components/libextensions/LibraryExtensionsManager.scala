package org.jetbrains.plugins.scala.components.libextensions

import java.io.File

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.lang.UrlClassLoader
import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.extensions.using
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}
import scala.util.{Failure, Success, Try}
import scala.xml._

class LibraryExtensionsManager(project: Project) extends AbstractProjectComponent(project) {
  import LibraryExtensionsManager._

  class ExtensionNotRegisteredException(iface: Class[_]) extends Exception(s"No extensions registered for class $iface")
  class InvalidExtensionException(iface: Class[_], impl: Class[_]) extends Exception(s"Extension $impl doesn't inherit $iface")

  private val LOG = Logger.getInstance(classOf[LibraryExtensionsManager])
  private val myAvailableLibraries = ArrayBuffer[LibraryDescriptor]()
  private val myExtensionInstances = mutable.HashMap[Class[_], ArrayBuffer[Any]]()
  private val myClassLoaders = mutable.HashMap[IdeaVersionDescriptor, UrlClassLoader]()
  private val myListeners = mutable.ArrayBuffer[Runnable]()

  private val properties: PropertiesComponent = PropertiesComponent.getInstance(project)

  override def projectOpened(): Unit = {
    ApplicationManager.getApplication.getMessageBus
      .syncPublisher(Notifications.TOPIC)
      .register(GROUP_ID, NotificationDisplayType.STICKY_BALLOON)
    loadCachedExtensions()
  }

  def searchExtensions(sbtResolvers: Set[SbtResolver]): Unit = {
    myAvailableLibraries.clear()
    myExtensionInstances.clear()
    myClassLoaders.clear()
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, "Searching for library extensions", false) {
        override def run(indicator: ProgressIndicator): Unit = doSearchExtensions(sbtResolvers)
    })
  }

  private def enabledAcceptCb(resolved: Seq[ResolvedDependency]): Unit = {
    resolved.foreach(processResolvedExtension)
    val jarPaths = resolved.map(_.file.getAbsolutePath).toArray
    properties.setValues("extensionJars", jarPaths)
    if (resolved.nonEmpty)
      myListeners.foreach(_.run())
  }

  private def enabledCancelledCb(): Unit = {
    ScalaProjectSettings.getInstance(project).setEnableLibraryExtensions(false)
  }

  private def doSearchExtensions(sbtResolvers: Set[SbtResolver]): Unit = {
    val allLibraries = ProjectLibraryTable.getInstance(project).getLibraries
    val ivyResolvers = sbtResolvers.toSeq.collect {
      case r: SbtMavenResolver => MavenResolver(r.name, r.root)
      case r: SbtIvyResolver if r.name != "Local cache" => IvyResolver(r.name, r.root)
    }

    val candidates = getExtensionLibCandidates(allLibraries)
    val resolved   = new IvyExtensionsResolver(ivyResolvers.reverse).resolve(candidates.toSeq:_*)
    val alreadyLoaded     = Option(properties.getValues("extensionJars")).map(_.toSet).getOrElse(Set.empty)
    val extensionsChanged = alreadyLoaded != resolved.map(_.file.getAbsolutePath).toSet

    if (resolved.nonEmpty && extensionsChanged)
      showEnablePopup({ () => enabledAcceptCb(resolved) }, enabledCancelledCb)
  }

  private def getExtensionLibCandidates(libs: Seq[Library]): Set[DependencyDescription] = {
    val patterns = ScalaProjectSettings.getInstance(project).getLextSearchPatterns.asScala

    def processLibrary(lib: Library): Seq[DependencyDescription] = lib.getName.split(": ?") match {
      case Array("sbt", org, module, version, "jar") =>
        val subst = patterns.map(_.replace(PAT_ORG, org).replace(PAT_MOD, stripScalaVersion(module)).replace(PAT_VER, version))
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
      case Right(descriptor)  => loadDescriptor(descriptor, jarFile)
    }
  }

  private def loadDescriptor(descriptor: LibraryDescriptor, jarFile: File): Unit = {
    myAvailableLibraries += descriptor
    descriptor.getCurrentPluginDescriptor.foreach { currentVersion =>
      val d@IdeaVersionDescriptor(_, _, pluginId, defaultPackage, extensions) = currentVersion
      extensions.foreach { e =>
        val ExtensionDescriptor(interface, impl, _, _, pluginId) = e
        val classLoader = UrlClassLoader.build()
          .urls(jarFile.toURI.toURL)
          .parent(getClass.getClassLoader)
          .useCache()
          .get()
        myClassLoaders += d -> classLoader
        val myInterface = classLoader.loadClass(interface)
        val myImpl = classLoader.loadClass(defaultPackage+impl)
        val myInstance = myImpl.newInstance()
        myExtensionInstances.getOrElseUpdate(myInterface, ArrayBuffer.empty) += myInstance
      }
    }
  }

  private def loadCachedExtensions(): Unit = {
    if (!ScalaProjectSettings.getInstance(project).isEnableLibraryExtensions) return
    val jarPaths = properties.getValues("extensionJars")
    if (jarPaths != null) {
      val fakeDependencies = jarPaths.map(path => ResolvedDependency(null, new File(path)))
      fakeDependencies.foreach(processResolvedExtension)
    }
  }

  def getExtensions[T](iface: Class[T]): Seq[T] = {
    myExtensionInstances.getOrElse(iface, Seq.empty).asInstanceOf[Seq[T]]
  }

  def getAvailableLibraries: Seq[LibraryDescriptor] = myAvailableLibraries

  def addNewExtensionsListener(runnable: Runnable): Unit = myListeners += runnable

  def removeNewExtensionsListener(runnable: Runnable): Unit = myListeners -= runnable

}

object LibraryExtensionsManager {

  val PAT_ORG = "$ORG"
  val PAT_MOD = "$MOD"
  val PAT_VER = "$VER"
  val PAT_SEP = "%"

  val DEFAULT_PATTERNS = Array(
    s"$PAT_ORG $PAT_SEP $PAT_MOD-ijext $PAT_SEP $PAT_VER+",
    s"org.jetbrains $PAT_SEP $PAT_MOD-ijext $PAT_SEP $PAT_VER+"
  )

  val manifestPath  = "META-INF/intellij-compat.xml"
  val GROUP_ID      = "Scala Library Extension"

  def getInstance(project: Project): LibraryExtensionsManager = project.getComponent(classOf[LibraryExtensionsManager])

  def showEnablePopup(yesCallback: () => Unit, noCallback: () => Unit): Unit = {
    val notification = new Notification(GROUP_ID, "Extensions available",
      s"""<p>Additional support has been found for some of your libraries.</p>
         |<p>Do you want to enable it? <a href="Yes">Yes</a> / <a href="No">No</a></p>
       """.stripMargin,
      NotificationType.INFORMATION, (notification: Notification, event: HyperlinkEvent) => {
        notification.expire()
        event.getDescription match {
          case "Yes" => yesCallback()
          case "No"  => noCallback()
        }
      }
    )
    Notifications.Bus.notify(notification)
  }

}
