package org.jetbrains.plugins.scala.components.libinjection

import java.io._
import java.net.URL
import java.util
import javax.swing.event.HyperlinkEvent

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module._
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.LibraryTable.Listener
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile, VirtualFileManager}
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorCompileHelper
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.collection.mutable

case class InjectorPersistentCache(pluginVersion: Version, cache: java.util.HashMap[String, JarManifest])

class LibraryInjectorLoader(val project: Project) extends ProjectComponent {

  class DynamicClassLoader(urls: Array[URL], parent: ClassLoader) extends java.net.URLClassLoader(urls, parent) {
    def addUrl(url: URL) = {
      super.addURL(url)
    }
  }

  type AttributedManifest = (JarManifest, Seq[InjectorDescriptor])
  type ManifestToDescriptors = Seq[AttributedManifest]

  val HELPER_LIBRARY_NAME    = "scala-plugin-dev"
  val INJECTOR_MANIFEST_NAME = "intellij-compat.xml"
  val INJECTOR_MODULE_NAME   = "ijscala-plugin-injector-compile.iml" // TODO: use UUID
  val myInjectorCacheDir     = new File(ScalaUtil.getScalaPluginSystemPath + "injectorCache/")
  val myInjectorCacheIndex   = new File(ScalaUtil.getScalaPluginSystemPath + "injectorCache/libs.index")
  private val myClassLoader  = new DynamicClassLoader(Array(myInjectorCacheDir.toURI.toURL), this.getClass.getClassLoader)
  private val LOG = Logger.getInstance(getClass)
  private val GROUP = new NotificationGroup("Injector", NotificationDisplayType.STICKY_BALLOON, false)

  // reset cache if plugin has been updated
  // cache: jarFilePath -> jarManifest
  private var jarCache: InjectorPersistentCache = null
  private val loadedInjectors: mutable.HashMap[Class[_], mutable.HashSet[String]] = mutable.HashMap()

  private val myLibraryTableListener = new Listener {
    override def afterLibraryRenamed(library: Library): Unit = ()

    override def beforeLibraryRemoved(library: Library): Unit = ()

    override def afterLibraryRemoved(library: Library): Unit = {
      if (library.getName != HELPER_LIBRARY_NAME)
        init()
    }

    override def afterLibraryAdded(newLibrary: Library): Unit = {
      if (newLibrary.getName != HELPER_LIBRARY_NAME)
        init()
    }
  }

  override def projectClosed(): Unit = {
    saveJarCache(jarCache, myInjectorCacheIndex)
  }

  override def projectOpened(): Unit = {
    jarCache = verifyLibraryCache(loadJarCache(myInjectorCacheIndex))
    init()
  }

  def init() = {
    DumbService.getInstance(project).smartInvokeLater {
      toRunnable {
        loadCachedInjectors()
        rescanAllJars()
      }
    }
  }

  override def initComponent(): Unit = {
    myInjectorCacheDir.mkdirs()
    LibraryTablesRegistrar.getInstance().getLibraryTable(project).addListener(myLibraryTableListener)
  }

  override def disposeComponent(): Unit = {
    LibraryTablesRegistrar.getInstance().getLibraryTable(project).removeListener(myLibraryTableListener)
  }

  override def getComponentName: String = "ScalaLibraryInjectorLoader"

  @inline def invokeLater(f: => Unit) = ApplicationManager.getApplication.invokeLater(toRunnable(f))

  @inline def toRunnable(f: => Unit) = new Runnable { override def run(): Unit = f }

  @inline def inReadAction(f: => Unit) = ApplicationManager.getApplication.runReadAction(toRunnable(f))

  @inline def inWriteAction[T](f: => T) = ApplicationManager.getApplication.runWriteAction(toRunnable(f))

  def getInjectorClasses[T](interface: Class[T]): Seq[Class[T]] = {
    loadedInjectors.getOrElse(interface, Seq.empty).map(myClassLoader.loadClass(_).asInstanceOf[Class[T]]).toSeq
  }

  def getInjectorInstances[T](interface: Class[T]): Seq[T] = {
    getInjectorClasses(interface).map(_.newInstance())
  }

  private def loadJarCache(f: File): InjectorPersistentCache = {
    var stream: ObjectInputStream = null
    try {
      stream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))
      val cache = stream.readObject().asInstanceOf[InjectorPersistentCache]
      LOG.trace(s"Loaded cache with ${cache.cache.size()} entries")
      cache
    } catch {
      case e: Throwable =>
        LOG.warn(s"Failed to load injector cache, continuing with empty(${e.getMessage})")
        InjectorPersistentCache(ScalaPluginVersionVerifier.getPluginVersion.get, new util.HashMap())
    } finally {
      if (stream != null) stream.close()
    }
  }

  private def saveJarCache(c: InjectorPersistentCache, f: File) = {
    val stream = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(f)))
    try {
      stream.writeObject(c)
    } catch {
      case e: Throwable =>
        LOG.error("Failed to save injector cache", e)
    } finally {
      stream.close()
    }
  }

  private def verifyLibraryCache(cache: InjectorPersistentCache): InjectorPersistentCache = {
    if (ScalaPluginVersionVerifier.getPluginVersion.exists(_ != cache.pluginVersion))
      InjectorPersistentCache(ScalaPluginVersionVerifier.getPluginVersion.get, new util.HashMap())
    else
      cache
  }

  private def verifyManifest(manifest: JarManifest): Option[JarManifest] = {
    def verifyInjector(injector: InjectorDescriptor): Option[InjectorDescriptor] = {
      if (injector.sources.isEmpty) {
        LOG.warn(s"Injector $injector has no sources, skipping")
        None
      } else {
        val sourcesValid = injector.sources.forall { source =>
          VirtualFileManager.getInstance.findFileByUrl(s"jar://${manifest.jarPath}!/$source") != null
        }
        if (!sourcesValid) {
          LOG.warn(s"Injector $injector has invalid source roots")
          None
        } else {
          try {
            myClassLoader.loadClass(injector.iface)
            Some(injector)
          } catch {
            case e: ClassNotFoundException =>
              LOG.warn(s"Interface class ${injector.iface} not found, skipping injector")
              None
            case e: Throwable =>
              LOG.warn(s"Error while verifying injector interface - ${e.getMessage}, skipping")
              None
          }
        }
      }
    }

    def verifyDescriptor(descriptor: PluginDescriptor): Option[PluginDescriptor] = {
      if (descriptor.since > descriptor.until || descriptor.since == descriptor.until) {
        LOG.warn(s"Plugin descriptor since >= until in $descriptor")
        None
      } else if (descriptor.injectors.isEmpty) {
        LOG.warn(s"Plugin descriptor has no injectors in $descriptor")
        None
      } else {
        val checkedInjectors = descriptor.injectors.flatMap(verifyInjector)
        if (checkedInjectors.nonEmpty)
          Some(descriptor.copy(injectors = checkedInjectors))
        else {
          LOG.warn(s"Descriptor $descriptor has no valid injectors, skipping")
          None
        }
      }
    }

    if (!new File(manifest.jarPath).exists())
      LOG.warn(s"Manifest has wrong JAR path(jar doesn't exist) - ${manifest.jarPath}")
    if (manifest.modTimeStamp > System.currentTimeMillis())
      LOG.warn(s"Manifest timestamp for ${manifest.jarPath} is in the future")
    if (manifest.pluginDescriptors.isEmpty) {
      LOG.warn(s"Manifest for ${manifest.jarPath} has no plugin descriptors")
    }

    val checkedDescriptor = findMatchingPluginDescriptor(manifest) match {
      case Some(descriptor) => verifyDescriptor(descriptor)
      case None =>
        LOG.info(s"No extensions found for current IDEA version")
        None
    }
    checkedDescriptor match {
      case Some(descriptor) => Some(manifest.copy(pluginDescriptors = Seq(descriptor))(manifest.isBlackListed))
      case None => None
    }
  }


  private def loadCachedInjectors() = {
    import scala.collection.JavaConversions._
    val allProjectJars = getAllJarsWithManifest.map(_.getPath).toSet
    val cachedProjectJars = jarCache.cache.filter(cacheItem => allProjectJars.contains(s"${cacheItem._1}!/")).values
    var numLoaded = 0
    for (manifest <- cachedProjectJars if !manifest.isBlackListed) {
      if (isJarCacheUpToDate(manifest)) {
        for (injector <- findMatchingInjectors(manifest)) {
          loadInjector(manifest, injector)
          numLoaded += 1
        }
      } else {
        jarCache.cache.remove(manifest.jarPath)
      }
    }
    LOG.trace(s"Loaded injectors from $numLoaded jars (${cachedProjectJars.size - numLoaded} filtered)")
  }

  private def rescanAllJars() = {
    val parsedManifests = getAllJarsWithManifest.flatMap(f=>extractLibraryManifest(f))
      .filterNot(jarCache.cache.values().contains)
    val validManifests = parsedManifests.flatMap(verifyManifest)
    val candidates = validManifests.map(manifest => manifest -> findMatchingInjectors(manifest))
    LOG.trace(s"Found ${candidates.size} new jars with embedded extensions")
    if (candidates.nonEmpty)
      askUser(candidates)
  }

  private def getAllJarsWithManifest: Seq[VirtualFile] = {
    val jarFS = JarFileSystem.getInstance
    val psiFiles = FilenameIndex.getFilesByName(project, INJECTOR_MANIFEST_NAME, GlobalSearchScope.allScope(project))
    psiFiles.map(f => jarFS.getJarRootForLocalFile(jarFS.getVirtualFileForJar(f.getVirtualFile)))
  }

  private def isJarCacheUpToDate(manifest: JarManifest): Boolean = {
    val jarFile = new File(manifest.jarPath)
    jarFile.exists() &&
      jarFile.isFile &&
      (jarFile.lastModified() == manifest.modTimeStamp) &&
      getLibraryCacheDir(jarFile).list().nonEmpty
  }

  private def extractLibraryManifest(jar: VirtualFile, skipIncompatible: Boolean = true): Option[JarManifest] = {
    val manifestFile = Option(jar.findFileByRelativePath(s"META-INF/$INJECTOR_MANIFEST_NAME"))
    manifestFile
      .map(JarManifest.deserialize(_, jar))
      .filterNot(m => skipIncompatible && findMatchingInjectors(m).isEmpty)
  }

  private def compileInjectorFromLibrary(sources: Seq[File], outDir: File, m: Module): Seq[File] = {
    val res = sources.flatMap(ScalaEvaluatorCompileHelper.instance(project).compile(_, m, outDir)).map(_._1)
    val parentDir = sources.headOption
    sources.foreach(_.delete())
    parentDir.foreach(_.delete())
    res
  }

  private def loadInjector(jarManifest: JarManifest, injectorDescriptor: InjectorDescriptor) = {
    myClassLoader.addUrl(getInjectorCacheDir(jarManifest)(injectorDescriptor).toURI.toURL)
    val injectors = findMatchingInjectors(jarManifest)
    for (injector <- injectors) {
      loadedInjectors
        .getOrElseUpdate(
          getClass.getClassLoader.loadClass(injector.iface),
          mutable.HashSet(injector.impl)
        ) += injector.impl
    }
  }

  private def findMatchingPluginDescriptor(libraryManifest: JarManifest): Option[PluginDescriptor] = {
    val curVer = ScalaPluginVersionVerifier.getPluginVersion
    libraryManifest.pluginDescriptors
      .find(d => (curVer.get > d.since && curVer.get < d.until) || curVer.get.isDebug)
  }

  private def findMatchingInjectors(libraryManifest: JarManifest): Seq[InjectorDescriptor] = {
    findMatchingPluginDescriptor(libraryManifest).map(_.injectors).getOrElse(Seq.empty)
  }

  // don't forget to remove temp directory after compilation
  private def extractInjectorSources(jar: File, injectorDescriptor: InjectorDescriptor): Seq[File] = {
    val tmpDir = File.createTempFile("inject", "")
    def copyToTmpDir(virtualFile: VirtualFile): File = {
      val target = new File(tmpDir, virtualFile.getName)
      val targetStream = new BufferedOutputStream(new FileOutputStream(target))
      try {
        target.createNewFile()
        FileUtil.copy(virtualFile.getInputStream, targetStream)
        target
      } finally {
        targetStream.close()
      }
    }
    if (tmpDir.delete() && tmpDir.mkdir()) {
      val root = VirtualFileManager.getInstance().findFileByUrl("jar://"+jar.getAbsolutePath+"!/")
      if (root != null) {
        injectorDescriptor.sources.flatMap(path => {
          Option(root.findFileByRelativePath(path)).map { f =>
            if (f.isDirectory)
              f.getChildren.filter(!_.isDirectory).map(copyToTmpDir).toSeq
            else
              Seq(copyToTmpDir(f))
          }.getOrElse(Seq.empty)
        })
      } else {
        LOG.error(s"Failed to locate source jar file - $jar")
        Seq.empty
      }
    } else {
      LOG.error(s"Failed to extract injector sources for ${injectorDescriptor.impl} - failed to create directory $tmpDir")
      Seq.empty
    }
  }

  private def askUser(candidates: ManifestToDescriptors) = {
    val message = s"Some of your project's libraries have IDEA support features.</p>Would you like to load them?" +
      s"""<p/><a href="Yes">Yes</a> """ +
      s"""<a href="No">No</a>"""
    val listener = new NotificationListener {
      override def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent): Unit = {
        notification.expire()
        if (event.getDescription == "Yes")
          compile(showReviewDialogAndFilter(candidates))
      }
    }
    GROUP.createNotification("IDEA Extensions", message, NotificationType.INFORMATION, listener).notify(project)
  }

  private def showReviewDialogAndFilter(candidates: ManifestToDescriptors): ManifestToDescriptors  = {
    val (accepted, rejected) = candidates.partition { candidate =>
      val dialog = new InjectorReviewDialog(project, candidate, LOG)
      dialog.showAndGet()
    }
    for ((manifest, _) <- rejected) {
      jarCache.cache.put(manifest.jarPath, manifest.copy()(isBlackListed = true))
    }
    accepted
  }

  private def compile(data: ManifestToDescriptors): Unit = {
    if (data.isEmpty) return
    val indicator = new ProgressIndicatorBase()
    val startTime = System.currentTimeMillis()
    LOG.trace(s"Compiling ${data.size} injectors")
    runWithHelperModule { module =>
      ProgressManager.getInstance().runProcess(toRunnable {
        indicator.setIndeterminate(true)
        for ((manifest, injectors) <- data) {
          for (injectorDescriptor <- injectors) {
            try {
              compileInjectorFromLibrary(
                extractInjectorSources(new File(manifest.jarPath), injectorDescriptor),
                getInjectorCacheDir(manifest)(injectorDescriptor),
                module
              )
              loadInjector(manifest, injectorDescriptor)
              jarCache.cache.put(manifest.jarPath, manifest)
            } catch {
              case e: Throwable =>
                LOG.error("Failed to compile injector", e)
            }
          }
        }
        LOG.trace(s"Compiled in ${(System.currentTimeMillis() - startTime) / 1000} seconds")
      }, indicator)
    }
  }

  private def getLibraryCacheDir(jar: File): File = {
    val file = new File(myInjectorCacheDir,
      (jar.getName + ScalaPluginVersionVerifier.getPluginVersion.get.toString).replaceAll("\\.", "_")
    )
    file.mkdir()
    file
  }

  private def getInjectorCacheDir(jarManifest: JarManifest)(injectorDescriptor: InjectorDescriptor): File = {
    val jarName = new File(jarManifest.jarPath).getName
    val pluginVersion = ScalaPluginVersionVerifier.getPluginVersion.get.toString
    val libraryDir = new File(myInjectorCacheDir, (jarName + pluginVersion).replaceAll("\\.", "_"))
    val injectorDir = new File(libraryDir, injectorDescriptor.impl.hashCode.toString)
    injectorDir.mkdirs()
    injectorDir
  }

  private def createIdeaModule(): Module = {
    import org.jetbrains.plugins.scala.project._

    import scala.collection.JavaConversions._

    val scalaSDK = project.modulesWithScala.head.scalaSdk.get
    val model  = ModuleManager.getInstance(project).getModifiableModel
    val module = model.newModule(project.getProjectFile.getParent.getPath + "/modules/"+ INJECTOR_MODULE_NAME, JavaModuleType.getModuleType.getId)
    model.commit()
    val urls   = this.getClass.getClassLoader.asInstanceOf[PluginClassLoader].getUrls.map(u=>s"jar://${u.getFile}!/")
    // get application classloader urls using reflection :(
    val parentUrls = ApplicationManager.getApplication.getClass.getClassLoader.getClass.getDeclaredMethods
      .find(_.getName == "getUrls")
      .map(_.invoke(ApplicationManager.getApplication.getClass.getClassLoader)
        .asInstanceOf[java.util.List[URL]])
    parentUrls.map(_.map(u=>s"jar://${u.getFile}!/")).foreach(urls.addAll(_))
    val lib    = module.createLibraryFromJar(urls, HELPER_LIBRARY_NAME)
    module.configureScalaCompilerSettingsFrom("Default", Seq())
    module.attach(lib)
    module.attach(scalaSDK)
    module.libraries
    module
  }

  private def removeIdeaModule() = {
    val libsModel = ProjectLibraryTable.getInstance(project).getModifiableModel
    val library   = libsModel.getLibraryByName(HELPER_LIBRARY_NAME)
    if (library != null) {
      libsModel.removeLibrary(library)
      libsModel.commit()
    } else {
      LOG.warn(s"Failed to remove helper library - $HELPER_LIBRARY_NAME not found")
    }

    val model  = ModuleManager.getInstance(project).getModifiableModel
    val module = model.findModuleByName(INJECTOR_MODULE_NAME.replaceAll("\\.iml$", ""))
    if (module != null) {
      model.disposeModule(module)
      model.commit()
    } else {
      LOG.warn(s"Failed to remove helper module - $INJECTOR_MODULE_NAME not found")
    }
  }

  private def runWithHelperModule[T](f: Module => T) = {
    inWriteAction {
      val module = createIdeaModule()
      try {
        f(module)
      } finally {
        removeIdeaModule()
      }
    }
  }

}

object LibraryInjectorLoader {
  def getInstance(project: Project) = project.getComponent(classOf[LibraryInjectorLoader])
}
