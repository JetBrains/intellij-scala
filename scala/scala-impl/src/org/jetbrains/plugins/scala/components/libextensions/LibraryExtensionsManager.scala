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
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier
import org.jetbrains.plugins.scala.components.libextensions.ui._
import org.jetbrains.plugins.scala.extensions.using
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, SAXParser}
import scala.xml.factory.XMLLoader

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
        override def run(indicator: ProgressIndicator): Unit = doSearchExtensions(sbtResolvers)
    })
  }

  private def enabledAcceptCb(resolved: Seq[ResolvedDependency]): Unit = {
    resolved.foreach(processResolvedExtension)
    properties.setValues(EXT_JARS_KEY, resolved.map(_.file.getAbsolutePath).toArray)
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
    val alreadyLoaded     = Option(properties.getValues(EXT_JARS_KEY)).map(_.toSet).getOrElse(Set.empty)
    val extensionsChanged = alreadyLoaded != resolved.map(_.file.getAbsolutePath).toSet

    if (resolved.nonEmpty && extensionsChanged)
      popup.showEnablePopup({ () => enabledAcceptCb(resolved) }, () => enabledCancelledCb())
  }

  private def getExtensionLibCandidates(libs: Seq[Library]): Set[DependencyDescription] = {
    val patterns = ScalaProjectSettings.getInstance(project).getLextSearchPatterns.asScala

    def processLibrary(lib: Library): Seq[DependencyDescription] = lib.getName.split(": ?") match {
      case Array("sbt", org, module, version, "jar") =>
        if (lib.getFiles(OrderRootType.CLASSES).exists(_.findChild("intellij-extension") != null)) {
          val subst = patterns.map(_.replace(PAT_ORG, org).replace(PAT_MOD, stripScalaVersion(module)).replace(PAT_VER, version))
          subst.map(_.split(s" *$PAT_SEP *")).collect {
            case Array(newOrg, newMod, newVer) => DependencyDescription(newOrg, newMod, newVer)
          }.toSeq
        } else {
          Seq.empty
        }
      case _ => Seq.empty
    }

    var resultSet = immutable.HashSet[DependencyDescription]()
    libs.foreach(resultSet ++= processLibrary(_))
    resultSet
  }

  private[libextensions] def processResolvedExtension(resolved: ResolvedDependency): Unit = {
    val file = resolved.toJarVFile
    val manifest = Option(file.findFileByRelativePath(manifestPath))
      .map(vFile => Try(using(vFile.getInputStream)(XMLNoDTD.load)))

    manifest match {
      case Some(Success(xml))       => loadJarWithManifest(xml, resolved.file)
      case Some(Failure(exception)) => LOG.error("Error parsing extensions manifest", exception)
      case None                     => LOG.error(s"No manifest in extensions jar ${resolved.file}")
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
      val fakeDependencies = existingFiles.map(file => ResolvedDependency(null, file))
      fakeDependencies.foreach(processResolvedExtension)
    }
  }

  def getExtensions[T](iface: Class[T]): Seq[T] = {
    myExtensionInstances.getOrElse(iface, Seq.empty).asInstanceOf[Seq[T]]
  }

  def getAvailableLibraries: Seq[LibraryDescriptor] = myAvailableLibraries

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

  def getInstance(project: Project): LibraryExtensionsManager = project.getComponent(classOf[LibraryExtensionsManager])

  val MOD_TRACKER = new ModificationTracker {
    private var modcount = 0l
    def incModCount(): Unit = modcount += 1
    override def getModificationCount: Long = modcount
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

}
