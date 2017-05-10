package org.jetbrains.plugins.scala.components.libinjection

import java.io._
import java.net.URL
import java.util
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module._
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.libraries.{Library, LibraryTable, LibraryTablesRegistrar}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile, VirtualFileManager}
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

@SerialVersionUID(-8361292897316544896L)
case class InjectorPersistentCache(pluginVersion: Version, cache: java.util.HashMap[String, JarManifest]) {
  def ensurePathExists(): Unit = {
   if (!LibraryInjectorLoader.myInjectorCacheDir.exists())
     FileUtil.createDirectory(LibraryInjectorLoader.myInjectorCacheDir)
  }
  def saveJarCache(): Unit = {
    ensurePathExists()
    val stream = new ObjectOutputStream(
      new BufferedOutputStream(
        new FileOutputStream(LibraryInjectorLoader.myInjectorCacheIndex)
      ))
    try {
      stream.writeObject(this)
      stream.flush()
    } catch {
      case e: Throwable =>
        Error.cacheSaveError(e)
    } finally {
      stream.close()
    }
  }
}
object InjectorPersistentCache {
  def loadJarCache: InjectorPersistentCache = {
    import LibraryInjectorLoader.LOG
    var stream: ObjectInputStream = null
    try {
      stream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(LibraryInjectorLoader.myInjectorCacheIndex)))
      val cache = stream.readObject().asInstanceOf[InjectorPersistentCache]
      LOG.trace(s"Loaded cache with ${cache.cache.size()} entries")
      cache
    } catch {
      case e: Throwable =>
        LOG.warn(s"Failed to load injector cache, continuing with empty(${e.getMessage})")
        InjectorPersistentCache(ScalaPluginVersionVerifier.getPluginVersion.getOrElse(Version.Snapshot), new util.HashMap())
    } finally {
      if (stream != null) stream.close()
    }
  }
}

class LibraryInjectorLoader(val project: Project) extends AbstractProjectComponent(project) {

  import LibraryInjectorLoader.{LOG, _}

  class DynamicClassLoader(urls: Array[URL], parent: ClassLoader) extends java.net.URLClassLoader(urls, parent) {
    def addUrl(url: URL): Unit = {
      super.addURL(url)
    }
  }

  type AttributedManifest = (JarManifest, Seq[InjectorDescriptor])
  type ManifestToDescriptors = Seq[AttributedManifest]

  private val myListeners = mutable.HashSet[InjectorsLoadedListener]()
  private val myClassLoader  = new DynamicClassLoader(Array(myInjectorCacheDir.toURI.toURL), this.getClass.getClassLoader)
  private val initialized = new AtomicBoolean(false)

  private val ackProvider = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      new TestAcknowledgementProvider
    else
      new UIAcknowledgementProvider(GROUP, project)(LOG)
  }

  // reset cache if plugin has been updated
  // cache: jarFilePath -> jarManifest
  private var jarCache: InjectorPersistentCache = null
  def getJarCache: InjectorPersistentCache = jarCache
  private val loadedInjectors: mutable.HashMap[Class[_], mutable.HashSet[String]] = mutable.HashMap()

  private val myLibraryTableListener = new LibraryTable.Listener {

    val skippedLibs = Array(HELPER_LIBRARY_NAME, "scala-sdk", ScalaLibraryName)

    override def afterLibraryRenamed(library: Library): Unit = ()

    override def beforeLibraryRemoved(library: Library): Unit = ()

    override def afterLibraryRemoved(newLibrary: Library): Unit = {
      if (!skippedLibs.contains(newLibrary.getName))
        initialized.set(false)
    }

    override def afterLibraryAdded(newLibrary: Library): Unit = {
      if (!skippedLibs.contains(newLibrary.getName))
        initialized.set(false)
    }
  }

  override def projectOpened(): Unit = {
    myInjectorCacheDir.mkdirs()
    LibraryTablesRegistrar.getInstance().getLibraryTable(project).addListener(myLibraryTableListener)
    jarCache = verifyAndLoadCache
    //    init()
  }

  override def projectClosed(): Unit = {
    jarCache.saveJarCache
    LibraryTablesRegistrar.getInstance().getLibraryTable(project).removeListener(myLibraryTableListener)
  }

  def addListener(l: InjectorsLoadedListener) {
    myListeners += l
  }
  
  def deleteListener(l: InjectorsLoadedListener) {
    myListeners remove l
  }

  def init(): Unit = {
    initialized.set(true)
    if (ScalaProjectSettings.getInstance(project).isEnableLibraryExtensions) {
      DumbService.getInstance(project).smartInvokeLater {
        toRunnable {
          loadCachedInjectors()
          rescanAllJars()
        }
      }
    }
  }
  
  def conditionalInit(): Unit = if (!initialized.get()) init()

  override def getComponentName: String = "ScalaLibraryInjectorLoader"

  @inline def invokeLater(f: => Unit): Unit = ApplicationManager.getApplication.invokeLater(toRunnable(f))

  @inline def toRunnable(f: => Unit) = new Runnable { override def run(): Unit = f }

  @inline def inReadAction(f: => Unit): Unit = ApplicationManager.getApplication.runReadAction(toRunnable(f))

  @inline def inWriteAction[T](f: => T): Unit = ApplicationManager.getApplication.runWriteAction(toRunnable(f))

  def getInjectorClasses[T](interface: Class[T]): Seq[Class[T]] = {
    if (!initialized.get()) init()
    loadedInjectors.getOrElse(interface, Seq.empty).map(myClassLoader.loadClass(_).asInstanceOf[Class[T]]).toSeq
  }

  def getInjectorInstances[T](interface: Class[T]): Seq[T] = {
    if (!initialized.get()) init()
    getInjectorClasses(interface).map(_.newInstance())
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
            case _: ClassNotFoundException =>
              LOG.warn(s"Interface class ${injector.iface} not found, skipping injector")
              None
            case NonFatal(e) =>
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

    if (!new File(manifest.jarPath).exists)
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
    checkedDescriptor.map(descriptor => manifest.copy(pluginDescriptors = Seq(descriptor))(manifest.isBlackListed, manifest.isLoaded))
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
          jarCache.cache.put(manifest.jarPath, manifest.copy()(isBlackListed = false, isLoaded = true))
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
      askUser(candidates) else myListeners.foreach(_.onLoadingCompleted())
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
    val platformJars = collectPlatformJars()
    CompileServerLauncher.ensureServerRunning(project)
    val connector = new InjectorServerConnector(m, sources, outDir, platformJars)
    try {
      connector.compile() match {
        case Left(output) => output.map(_._1)
        case Right(errors) => throw EvaluationException(errors.mkString("\n"))
      }
    }
    catch {
      case e: Exception => Error.compilationError("Could not compile:\n" + e.getMessage)
    }
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
    val curVer = ScalaPluginVersionVerifier.getPluginVersion.getOrElse(Version.Snapshot)
    libraryManifest.pluginDescriptors
      .find(d => (curVer > d.since && curVer < d.until) || curVer.isSnapshot)
  }

  private def findMatchingInjectors(libraryManifest: JarManifest): Seq[InjectorDescriptor] = {
    findMatchingPluginDescriptor(libraryManifest).map(_.injectors).getOrElse(Seq.empty)
  }

  // don't forget to remove temp directory after compilation
  private def extractInjectorSources(jar: File, injectorDescriptor: InjectorDescriptor): Seq[File] = {
    val tmpDir = ScalaUtil.createTmpDir("inject")
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
    if (tmpDir.exists()) {
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
        Error.noJarFound(jar)
      }
    } else {
      Error.extractFailed(injectorDescriptor.impl, tmpDir)
    }
  }

  private def askUser(candidates: ManifestToDescriptors) = {
    ackProvider.askGlobalInjectorEnable(acceptCallback = compile(showReviewDialogAndFilter(candidates)))
  }

  private def showReviewDialogAndFilter(candidates: ManifestToDescriptors): ManifestToDescriptors  = {
    val (accepted, rejected) = ackProvider.showReviewDialogAndFilter(candidates)
    for ((manifest, _) <- rejected) {
      jarCache.cache.put(manifest.jarPath, manifest.copy()(isBlackListed = true, isLoaded = false))
    }
    accepted
  }

  private def compile(data: ManifestToDescriptors): Unit = {
    if (data.isEmpty) return
    val indicator = new ProgressIndicatorBase()
    indicator.setIndeterminate(true)
    val startTime = System.currentTimeMillis()
    var numSuccessful, numFailed = 0
    LOG.trace(s"Compiling ${data.size} injectors from ${data.size} jars")
    runWithHelperModule { module =>
      ProgressManager.getInstance().runProcess(toRunnable {
        for ((manifest, injectors) <- data) {
          for (injectorDescriptor <- injectors) {
            try {
              compileInjectorFromLibrary(
                extractInjectorSources(new File(manifest.jarPath), injectorDescriptor),
                getInjectorCacheDir(manifest)(injectorDescriptor),
                module
              )
              numSuccessful += 1
              loadInjector(manifest, injectorDescriptor)
              jarCache.cache.put(manifest.jarPath, manifest.copy()(isBlackListed = false, isLoaded = true))
            } catch {
              case e: InjectorCompileException =>
                LOG.error("Failed to compile injector", e)
                numFailed += 1
            }
          }
        }
        val msg = if (numFailed == 0)
            s"Compiled $numSuccessful injector(s) in ${(System.currentTimeMillis() - startTime) / 1000} seconds"
          else
            s"Failed to compile $numFailed injectors out of ${numSuccessful+numFailed}, see Event Log for details"
        val notificationDisplayType = if (numFailed == 0) NotificationType.INFORMATION else NotificationType.ERROR
        GROUP.createNotification("IDEA Extensions", msg, notificationDisplayType, null).notify(project)
        LOG.trace(msg)
        
        myListeners.foreach(_.onLoadingCompleted())
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
    val injectorDir = new File(libraryDir, injectorDescriptor.impl.hashCode.abs.toString)
    injectorDir.mkdirs()
    injectorDir
  }

  private def collectPlatformJars(): Seq[File] = {
    import scala.collection.JavaConversions._

    val buffer: ArrayBuffer[File] = mutable.ArrayBuffer()

    // these are actually different classes calling different methods which are surprisingly called the same
    this.getClass.getClassLoader match {
      case cl: PluginClassLoader =>
        buffer ++= cl.getUrls.map(u => new File(u.getFile))
      case cl: java.net.URLClassLoader =>
        buffer ++= cl.getURLs.map(u => new File(u.getFile))
    }
    // get application classloader urls using reflection :(
    ApplicationManager.getApplication.getClass.getClassLoader match {
      case cl: java.net.URLClassLoader =>
        val v = cl.getClass.getMethods.find(_.getName == "getURLs")
          .map(_.invoke(ApplicationManager.getApplication.getClass.getClassLoader)
            .asInstanceOf[Array[URL]].map(u => new File(u.getFile))).getOrElse(Array())
        buffer ++= v
      case cl: com.intellij.util.lang.UrlClassLoader =>
        val v = cl.getClass.getMethods.find(_.getName == "getUrls")
          .map(_.invoke(ApplicationManager.getApplication.getClass.getClassLoader)
            .asInstanceOf[java.util.List[URL]].map(u => new File(u.getFile))).getOrElse(Seq.empty)
        buffer ++= v
      case other =>
          val v = other.getClass.getMethods.find(_.getName == "getUrls")
            .map(_.invoke(ApplicationManager.getApplication.getClass.getClassLoader)
              .asInstanceOf[java.util.List[URL]].map(u => new File(u.getFile))).getOrElse(Seq.empty)
        buffer ++= v
    }
    buffer
  }

  private def createIdeaModule(): Module = {
    import org.jetbrains.plugins.scala.project._

    val scalaSDK = project.modulesWithScala.head.scalaSdk.get
    val model = project.modifiableModel
    val module = model.newModule(ScalaUtil.createTmpDir("injectorModule").getAbsolutePath +
      "/" + INJECTOR_MODULE_NAME, JavaModuleType.getModuleType.getId)
    model.commit()
    module.configureScalaCompilerSettingsFrom("Default", Seq())
    module.attach(scalaSDK)
    module
  }

  private def removeIdeaModule() = {
    val model = project.modifiableModel
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
  trait InjectorsLoadedListener {
    def onLoadingCompleted(): Unit
  }

  val HELPER_LIBRARY_NAME    = "scala-plugin-dev"
  val INJECTOR_MANIFEST_NAME = "intellij-compat.xml"
  val INJECTOR_MODULE_NAME   = "ijscala-plugin-injector-compile.iml" // TODO: use UUID
  val myInjectorCacheDir     = new File(ScalaUtil.getScalaPluginSystemPath + "injectorCache/")
  val myInjectorCacheIndex   = new File(ScalaUtil.getScalaPluginSystemPath + "injectorCache/libs.index")
  implicit val LOG: Logger = Logger.getInstance(getClass)
  private val GROUP = new NotificationGroup("Injector", NotificationDisplayType.STICKY_BALLOON, false)

  def getInstance(project: Project): LibraryInjectorLoader = project.getComponent(classOf[LibraryInjectorLoader])

  private def verifyLibraryCache(cache: InjectorPersistentCache): InjectorPersistentCache = {
    if (ScalaPluginVersionVerifier.getPluginVersion.exists(_ != cache.pluginVersion))
      InjectorPersistentCache(ScalaPluginVersionVerifier.getPluginVersion.get, new util.HashMap())
    else
      cache
  }

  def verifyAndLoadCache: InjectorPersistentCache = {
    verifyLibraryCache(InjectorPersistentCache.loadJarCache)
  }
}
