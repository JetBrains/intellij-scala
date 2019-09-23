package org.jetbrains.plugins.scala.components.libextensions

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.{Library, LibraryTable}
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier
import org.jetbrains.plugins.scala.components.libextensions.ui._
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.using
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.SbtResolver

import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, SAXParser}

class LibraryExtensionsManager(project: Project) extends ProjectComponent {
  import LibraryExtensionsManager._

  private val EXT_JARS_KEY = "extensionJars"

  private val LOG         = Logger.getInstance(classOf[LibraryExtensionsManager])
  private val properties  = PropertiesComponent.getInstance(project)
  private val popup       = new PopupHelper
  private val publisher   = project.getMessageBus.syncPublisher(EXTENSIONS_TOPIC)

  private val myExtensionInstances  = mutable.HashMap[Class[_], mutable.ArrayBuffer[Any]]()
  private val myLoadedLibraries     = mutable.ArrayBuffer[ExtensionJarData]()

  private object XMLNoDTD extends XMLLoader[Elem] {
    override def parser: SAXParser = {
      val f = javax.xml.parsers.SAXParserFactory.newInstance()
      f.setValidating(false)
      f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      f.newSAXParser()
    }
  }

  private object LibraryListener extends LibraryTable.Listener {
    private val accessed = new AtomicBoolean(false)
    override def afterLibraryAdded(newLibrary: Library): Unit = action()
    override def afterLibraryRemoved(library: Library): Unit = action()
    override def afterLibraryRenamed(library: Library): Unit = action()

    private def action(): Unit = {
      if (accessed.compareAndSet(false, true))
        DumbService.getInstance(project).smartInvokeLater( extensions.toRunnable {
          val allProjectResolvers = ModuleManager.getInstance(project)
            .getModules
            .toSet
            .flatMap(SbtModule.Resolvers.apply)

          LibraryExtensionsManager.getInstance(project)
            .searchExtensions(allProjectResolvers)
          accessed.set(false)
        })
    }
  }

  override def projectOpened(): Unit = {
    ApplicationManager.getApplication.getMessageBus
      .syncPublisher(Notifications.TOPIC)
      .register(PopupHelper.GROUP_ID, NotificationDisplayType.STICKY_BALLOON)
    if (ScalaProjectSettings.getInstance(project).isEnableLibraryExtensions)
      loadCachedExtensions()
    ProjectLibraryTable.getInstance(project).addListener(LibraryListener, project)
  }

  def setEnabled(value: Boolean): Unit = {
    myExtensionInstances.clear()
    myLoadedLibraries.clear()
    MOD_TRACKER.incModCount()
    try   { if (value) loadCachedExtensions() }
    catch { case e: Exception => LOG.error(s"Failed to load cached extensions", e) }
  }

  def searchExtensions(sbtResolvers: Set[SbtResolver]): Unit = {
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, "Searching for library extensions", true) {
        override def run(indicator: ProgressIndicator): Unit = {
          implicit val project: Project = myProject
          val resolved              = new ExtensionDownloader(indicator, sbtResolvers).getExtensionJars
          val alreadyLoadedSet      = myLoadedLibraries.map(_.file).toSet
          val newExtensionsSet      = resolved.toSet
          val libsAdded       = newExtensionsSet &~ alreadyLoadedSet
          val libsRemoved     = alreadyLoadedSet &~ newExtensionsSet
          val extensionsChanged = libsAdded.nonEmpty || libsRemoved.nonEmpty
          if (extensionsChanged) {
            myExtensionInstances.clear()
            myLoadedLibraries.clear()
            saveCachedExtensions()
            if (libsAdded.nonEmpty)
              popup.showEnablePopup({ () => enabledAcceptCb(resolved) }, () => enabledCancelledCb())
          }
        }
    })
  }

  private def enabledAcceptCb(resolved: Seq[File]): Unit = {
    resolved.foreach(processResolvedExtensionWithLogging)
    saveCachedExtensions()
    publisher.newExtensionsAdded()
  }

  private def enabledCancelledCb(): Unit = {
    ScalaProjectSettings.getInstance(project).setEnableLibraryExtensions(false)
  }

  private[libextensions] def processResolvedExtension(resolved: File): Unit = {
    if (myLoadedLibraries.exists(_.file.getAbsolutePath == resolved.getAbsolutePath))
      throw new ExtensionAlreadyLoadedException(resolved)
    val vFile = JarFileSystem.getInstance().findLocalVirtualFileByPath(resolved.getAbsolutePath)
    val manifest = Option(vFile.findFileByRelativePath(MANIFEST_PATH))
      .map(vFile => Try(using(vFile.getInputStream)(XMLNoDTD.load)))

    manifest match {
      case Some(Success(xml))       => loadJarWithManifest(xml, resolved)
      case Some(Failure(exception)) => throw new BadManifestException(resolved, exception)
      case None                     => throw new NoManifestInExtensionJarException(resolved)
    }
    MOD_TRACKER.incModCount()
  }

  private def processResolvedExtensionWithLogging(resolved: File): Unit = try {
    processResolvedExtension(resolved)
  } catch {
    case _: ProcessCanceledException  =>
    case NonFatal(t)                  => LOG.error(t)
  }

  private def loadJarWithManifest(manifest: Elem, jarFile: File): Unit = {
    LibraryDescriptor.parse(manifest) match {
      case Left(error)        => throw new BadExtensionDescriptor(jarFile, error)
      case Right(descriptor)  => loadDescriptor(descriptor, jarFile)
    }
  }

  private def loadDescriptor(descriptor: LibraryDescriptor, jarFile: File): Unit = {
    var classBuffer = mutable.HashMap[Class[_], mutable.ArrayBuffer[Any]]()
    descriptor.getCurrentPluginDescriptor.foreach { currentVersion =>
      val IdeaVersionDescriptor(_, _, _, defaultPackage, extensions) = currentVersion
      val classLoader = UrlClassLoader.build()
        .urls(jarFile.toURI.toURL)
        .parent(getClass.getClassLoader)
        .useCache()
        .get()
      extensions.filter(_.isAvailable).foreach { e =>
        val ExtensionDescriptor(interface, impl, _, _, _) = e
        val myInterface = classLoader.loadClass(interface)
        val myImpl = classLoader.loadClass(defaultPackage + impl)
        val myInstance = myImpl.newInstance()
        classBuffer.getOrElseUpdate(myInterface, mutable.ArrayBuffer.empty) += myInstance
      }
    }
    myExtensionInstances ++= classBuffer
    myLoadedLibraries    +=  ExtensionJarData(descriptor, jarFile, classBuffer.toMap)
  }

  private def saveCachedExtensions(): Unit = {
    properties.setValues(EXT_JARS_KEY, myLoadedLibraries.map(_.file.getAbsolutePath).toArray)
  }

  private def loadCachedExtensions(): Unit = {
    val jarPaths = properties.getValues(EXT_JARS_KEY)
    if (jarPaths != null) {
      val existingFiles = jarPaths.map(new File(_)).filter(_.exists())
      properties.setValues(EXT_JARS_KEY, existingFiles.map(_.getAbsolutePath))
      existingFiles.foreach(processResolvedExtensionWithLogging)
    }
  }

  def getExtensions[T](iface: Class[T]): Seq[T] = {
    myExtensionInstances.getOrElse(iface, Seq.empty).asInstanceOf[Seq[T]]
  }

  def removeExtension(jarData: ExtensionJarData): Unit = {
    val extensionData = myLoadedLibraries.find(_ == jarData)
    extensionData match {
      case Some(data) =>
        for {
          key <- data.loadedExtensions.keys
          instances = data.loadedExtensions.getOrElse(key, mutable.ArrayBuffer.empty)
        } { myExtensionInstances.getOrElse(key, mutable.ArrayBuffer.empty) --= instances }
        myLoadedLibraries -= data
        saveCachedExtensions()
      case None =>
        throw new ExtensionException(s"Remove failed: requested extension library is not loaded\n$jarData")
    }
    saveCachedExtensions()
  }

  def getAvailableLibraries: Seq[ExtensionJarData] = myLoadedLibraries

  def addExtension(file: File): Unit = {
    processResolvedExtension(file)
    saveCachedExtensions()
  }

}

object LibraryExtensionsManager {

  val EXTENSIONS_TOPIC = new Topic[LibraryExtensionsListener]("EXTENSIONS_TOPIC", classOf[LibraryExtensionsListener])

  private[libextensions] val BUNDLED_INDEX_PATH = "META-INF/extension-registry.json"
  private[libextensions] val MANIFEST_PATH      = "META-INF/intellij-compat.xml"
  private[libextensions] val PROPS_NAME         = "intellij-compat.json"

  def getInstance(project: Project): LibraryExtensionsManager = project.getComponent(classOf[LibraryExtensionsManager])

  //noinspection TypeAnnotation
  val MOD_TRACKER = new ModificationTracker {
    private var modCount = 0l
    def incModCount(): Unit = modCount += 1
    override def getModificationCount: Long = modCount
  }

  implicit class LibraryDescriptorExt(private val ld: LibraryDescriptor) extends AnyVal {
    import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version
    def getCurrentPluginDescriptor: Option[IdeaVersionDescriptor] = ld.pluginVersions.find { descr =>
      ScalaPluginVersionVerifier.getPluginVersion match {
        case Some(Version.Snapshot) => true
        case Some(ver)              => ver.compare(descr.sinceBuild) >= 0 && ver.compare(descr.untilBuild) <= 0
        case _                      => false
      }
    }
  }

  implicit class ExtensionDescriptorEx(private val ed: ExtensionDescriptor) extends AnyVal {
    def isAvailable: Boolean = ed.pluginId.isEmpty || PluginManagerCore.isPluginInstalled(PluginId.getId(ed.pluginId))
  }

  implicit class StringEx(private val str: String) extends AnyVal {
    def toDepDescription: DependencyDescription = {
      val parts = str.replaceAll("\\s+", "").split('%')
      if (parts.length != 3)
        throw new RuntimeException(s"Couldn't parse dependency from string: $str")
      DependencyDescription(org = parts(0), artId = parts(1), version = parts(2))
    }
  }

}
