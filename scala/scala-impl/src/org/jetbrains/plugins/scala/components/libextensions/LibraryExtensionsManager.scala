package org.jetbrains.plugins.scala.components.libextensions

import java.io.File

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier
import org.jetbrains.plugins.scala.components.libextensions.ui._
import org.jetbrains.plugins.scala.extensions.using
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.resolvers.SbtResolver

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, SAXParser}

class LibraryExtensionsManager(project: Project) extends ProjectComponent {
  import LibraryExtensionsManager._

  class ExtensionNotRegisteredException(iface: Class[_]) extends Exception(s"No extensions registered for class $iface")
  class InvalidExtensionException(iface: Class[_], impl: Class[_]) extends Exception(s"Extension $impl doesn't inherit $iface")

  private val EXT_JARS_KEY = "extensionJars"

  private val LOG         = Logger.getInstance(classOf[LibraryExtensionsManager])
  private val properties  = PropertiesComponent.getInstance(project)
  private val popup       = new PopupHelper

  private val myAvailableLibraries  = ArrayBuffer[LibraryDescriptor]()
  private val myExtensionInstances  = mutable.HashMap[Class[_], ArrayBuffer[Any]]()
  private val myClassLoaders        = mutable.HashMap[IdeaVersionDescriptor, UrlClassLoader]()

  private object XMLNoDTD extends XMLLoader[Elem] {
    override def parser: SAXParser = {
      val f = javax.xml.parsers.SAXParserFactory.newInstance()
      f.setValidating(false)
      f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      f.newSAXParser()
    }
  }

  override def projectOpened(): Unit = {
    ApplicationManager.getApplication.getMessageBus
      .syncPublisher(Notifications.TOPIC)
      .register(PopupHelper.GROUP_ID, NotificationDisplayType.STICKY_BALLOON)
    if (ScalaProjectSettings.getInstance(project).isEnableLibraryExtensions)
      loadCachedExtensions()
  }

  def setEnabled(value: Boolean): Unit = {
    myAvailableLibraries.clear()
    myExtensionInstances.clear()
    myClassLoaders.clear()
    MOD_TRACKER.incModCount()
    try   { if (value) loadCachedExtensions() }
    catch { case e: Exception => LOG.error(s"Failed to load cached extensions", e) }
  }

  def searchExtensions(sbtResolvers: Set[SbtResolver]): Unit = {
    myAvailableLibraries.clear()
    myExtensionInstances.clear()
    myClassLoaders.clear()
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, "Searching for library extensions", false) {
        override def run(indicator: ProgressIndicator): Unit = {
          implicit val project: Project = myProject
          val resolved   = new ExtensionDownloader(indicator, sbtResolvers).getExtensionJars
          val alreadyLoaded     = Option(properties.getValues(EXT_JARS_KEY)).map(_.toSet).getOrElse(Set.empty)
          val extensionsChanged = alreadyLoaded != resolved.map(_.getAbsolutePath).toSet
          if (resolved.nonEmpty && extensionsChanged)
            popup.showEnablePopup({ () => enabledAcceptCb(resolved) }, () => enabledCancelledCb())
        }
    })
  }

  private def enabledAcceptCb(resolved: Seq[File]): Unit = {
    resolved.foreach(processResolvedExtension)
    properties.setValues(EXT_JARS_KEY, resolved.map(_.getAbsolutePath).toArray)
  }

  private def enabledCancelledCb(): Unit = {
    ScalaProjectSettings.getInstance(project).setEnableLibraryExtensions(false)
  }

  private[libextensions] def processResolvedExtension(resolved: File): Unit = {
    val vFile = JarFileSystem.getInstance().findLocalVirtualFileByPath(resolved.getAbsolutePath)
    val manifest = Option(vFile.findFileByRelativePath(MANIFEST_PATH))
      .map(vFile => Try(using(vFile.getInputStream)(XMLNoDTD.load)))

    manifest match {
      case Some(Success(xml))       => loadJarWithManifest(xml, resolved)
      case Some(Failure(exception)) => LOG.error("Error parsing extensions manifest", exception)
      case None                     => LOG.error(s"No manifest in extensions jar $resolved")
    }
    MOD_TRACKER.incModCount()
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
      val classLoader = UrlClassLoader.build()
        .urls(jarFile.toURI.toURL)
        .parent(getClass.getClassLoader)
        .useCache()
        .get()
      myClassLoaders += d -> classLoader
      extensions.filter(_.isAvailable).foreach { e =>
        val ExtensionDescriptor(interface, impl, _, _, _) = e
        try {
          val myInterface = classLoader.loadClass(interface)
          val myImpl = classLoader.loadClass(defaultPackage + impl)
          val myInstance = myImpl.newInstance()
          myExtensionInstances.getOrElseUpdate(myInterface, ArrayBuffer.empty) += myInstance
        } catch {
          case e: Exception => LOG.error(s"Failed to load injector '$impl'", e)
        }
      }
    }
  }

  private def loadCachedExtensions(): Unit = {
    val jarPaths = properties.getValues(EXT_JARS_KEY)
    if (jarPaths != null) {
      val existingFiles = jarPaths.map(new File(_)).filter(_.exists())
      properties.setValues(EXT_JARS_KEY, existingFiles.map(_.getAbsolutePath))
      existingFiles.foreach(processResolvedExtension)
    }
  }

  def getExtensions[T](iface: Class[T]): Seq[T] = {
    myExtensionInstances.getOrElse(iface, Seq.empty).asInstanceOf[Seq[T]]
  }

  def getAvailableLibraries: Seq[LibraryDescriptor] = myAvailableLibraries

}

object LibraryExtensionsManager {

  val MANIFEST_PATH  = "META-INF/intellij-compat.xml"
  val PROPS_NAME     = "intellij-compat.json"

  def getInstance(project: Project): LibraryExtensionsManager = project.getComponent(classOf[LibraryExtensionsManager])

  //noinspection TypeAnnotation
  val MOD_TRACKER = new ModificationTracker {
    private var modCount = 0l
    def incModCount(): Unit = modCount += 1
    override def getModificationCount: Long = modCount
  }

  implicit class LibraryDescriptorExt(val ld: LibraryDescriptor) extends AnyVal {
    import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version
    def getCurrentPluginDescriptor: Option[IdeaVersionDescriptor] = ld.pluginVersions.find { descr =>
      ScalaPluginVersionVerifier.getPluginVersion match {
        case Some(Version.Snapshot) => true
        case Some(ver)              => ver.compare(descr.sinceBuild) >= 0 && ver.compare(descr.untilBuild) <= 0
        case _                      => false
      }
    }
  }

  implicit class ExtensionDescriptorEx(val ed: ExtensionDescriptor) extends AnyVal {
    def isAvailable: Boolean = ed.pluginId.isEmpty || PluginManager.isPluginInstalled(PluginId.getId(ed.pluginId))
  }

  implicit class StringEx(val str: String) extends AnyVal {
    def toDepDescription: DependencyDescription = {
      val parts = str.replaceAll("\\s+", "").split('%')
      if (parts.length != 3)
        throw new RuntimeException(s"Couldn't parse dependency from string: $str")
      DependencyDescription(org = parts(0), artId = parts(1), version = parts(2))
    }
  }

}
