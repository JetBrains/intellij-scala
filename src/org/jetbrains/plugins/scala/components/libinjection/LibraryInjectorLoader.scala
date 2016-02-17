package org.jetbrains.plugins.scala.components.libinjection

import java.io._
import java.net.{URL, URLClassLoader}

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module._
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.collection.mutable

class LibraryInjectorLoader(val project: Project) extends ProjectComponent {

  class DynamicClassLoader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {
    def addUrl(url: URL) = {
      super.addURL(url)
    }
  }

  val MAX_JARS               = 16 // dirty hack to avoid slowdowns on enormous libs such as scala-plugin's unmanaged-jars
  val INJECTOR_MANIFEST_NAME = "intellij-compat.xml"
  val injectorModuleName     = "ijscala-plugin-injector-compile.iml" // TODO: use UUID
  val myInjectorCacheDir     = new File(ScalaUtil.getScalaPluginSystemPath + "injectorCache/")
  val myInjectorCacheIndex   = new File(ScalaUtil.getScalaPluginSystemPath + "injectorCache/libs.index")
  private val myClassLoader  = new DynamicClassLoader(Array(myInjectorCacheDir.toURI.toURL), project.getClass.getClassLoader)
  private val LOG = Logger.getInstance(getClass)

  private var jarCache: InjectorPersistentCache = null

  // reset cache if plugin has been updated
  // cache: jarFilePath -> jarManifest
  case class InjectorPersistentCache(pluginVersion: Version, cache: mutable.HashMap[String, JarManifest])

  override def projectClosed(): Unit = {
    saveJarCache(jarCache, myInjectorCacheIndex)
  }

  override def projectOpened(): Unit = {
    jarCache = verifyLibraryCache(loadJarCache(myInjectorCacheIndex))
    loadCachedInjectors()
    invokeLater {
      rescanAllJars()
    }
  }

  override def initComponent(): Unit = {
    myInjectorCacheDir.mkdirs()
  }

  override def disposeComponent(): Unit = {

  }

  override def getComponentName: String = "ScalaLibraryInjectorLoader"

  @inline def invokeLater(f: => Unit) = ApplicationManager.getApplication.executeOnPooledThread(toRunnable(f))

  @inline def toRunnable(f: => Unit) = new Runnable { override def run(): Unit = f }

  private def loadJarCache(f: File): InjectorPersistentCache = {
    try {
      val stream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))
      stream.readObject().asInstanceOf[InjectorPersistentCache]
    } catch {
      case e: Throwable =>
        LOG.warn("Failed to load injector cache, continuing with empty", e)
        InjectorPersistentCache(ScalaPluginVersionVerifier.getPluginVersion.get, mutable.HashMap.empty)
    }
  }

  private def saveJarCache(c: InjectorPersistentCache, f: File) = {
    try {
      val stream = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(f)))
      stream.writeObject(c)
    } catch {
      case e: Throwable => LOG.error("Failed to save injector cache", e)
    }
  }

  private def verifyLibraryCache(cache: InjectorPersistentCache): InjectorPersistentCache = {
    if (ScalaPluginVersionVerifier.getPluginVersion.exists(_ != cache.pluginVersion))
      InjectorPersistentCache(ScalaPluginVersionVerifier.getPluginVersion.get, mutable.HashMap.empty)
    else
      cache
  }

  private def loadCachedInjectors() = {
    val libs = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries
    val allProjectJars = libs.flatMap(getJarsFromLibrary).map(_.getPath).toSet
    val cachedProjectJars = jarCache.cache.filter(c=>allProjectJars.contains(c._1)).values
    for (manifest <- cachedProjectJars) {
      if (isJarCacheUpToDate(manifest))
        loadInjectors(manifest)
      else
        jarCache.cache.remove(manifest.jarPath)
    }
  }

  private def rescanAllJars() = {
    val libs = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries
    val allJars = libs.flatMap(getJarsFromLibrary)
    val jarsWithManifest = allJars.flatMap(l => extractLibraryManifest(l))
    val outdatedJars = jarsWithManifest.filter(lm => isJarCacheUpToDate(lm))
    for (manifest <- outdatedJars) {
      findMatchingInjectors(manifest) match {
        case descriptors =>
          descriptors
            .filter(askUser)
            .foreach { descriptor =>
              compileInjectorFromLibrary(extractInjectorSources(new File(manifest.jarPath), descriptor))
              loadInjectors(manifest)
              jarCache.cache(manifest.jarPath) = manifest
            }
        case Seq.empty =>
      }
    }
  }

  def getJarsFromLibrary(library: Library): Seq[VirtualFile] = {
    val files = library.getFiles(OrderRootType.CLASSES)
    if (files.length < MAX_JARS)
      files.toSeq
    else
      Seq.empty
  }

  def isJarCacheUpToDate(manifest: JarManifest): Boolean = {
    val jarFile = new File(manifest.jarPath)
    jarFile.exists() &&
      jarFile.isFile &&
      (jarFile.lastModified() == manifest.modTimeStamp) &&
      getLibraryCacheDir(jarFile).list().nonEmpty
  }

  def extractLibraryManifest(jar: VirtualFile, skipIncompatible: Boolean = true): Option[JarManifest] = {
    val manifestFile = Option(jar.findChild(INJECTOR_MANIFEST_NAME))
    manifestFile
      .map(JarManifest.deserialize(_, jar))
      .filterNot(m => skipIncompatible && findMatchingInjectors(m).isEmpty)
  }

  private def compileInjectorFromLibrary(sources: Seq[File]): Seq[File] = {
    ???
  }

  private def loadInjectors(jarManifest: JarManifest) = {
    myClassLoader.addUrl(getLibraryCacheDir(new File(jarManifest.jarPath)).toURI.toURL)

    ???
  }

  private def findMatchingInjectors(libraryManifest: JarManifest): Seq[InjectorDescriptor] = {
    val curVer = ScalaPluginVersionVerifier.getPluginVersion
    libraryManifest.pluginDescriptors
      .find(d => curVer.get > d.since && curVer.get < d.until)
        .map(_.injectors).getOrElse(Seq.empty)
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
    if (tmpDir.mkdir()) {
      val root = VirtualFileManager.getInstance().findFileByUrl(jar.toURI.toURL.toString)
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

  private def askUser(injectorDescriptor: InjectorDescriptor): Boolean = {
    true // TODO: GUI
  }

  def getLibraryCacheDir(jar: File): File = {
    val f = new File(myInjectorCacheDir,
      (jar.getName + ScalaPluginVersionVerifier.getPluginVersion.get.toString).replaceAll(".", "_"))
    f.mkdir()
    f
  }

  def createIdeaModule(): Module = {
    import org.jetbrains.plugins.scala.project._
    import scala.collection.JavaConversions._

    val model  = ModuleManager.getInstance(project).getModifiableModel
    val module = model.newModule(injectorModuleName, JavaModuleType.getModuleType.getId)
    val urls   = this.getClass.getClassLoader.asInstanceOf[PluginClassLoader].getUrls
//    urls.addAll(module.getClass.lo)
    val lib    = module.createLibraryFromJarUrl(urls, "scala-plugin-dev")
    module.attach(lib)
    module.attach(project.modulesWithScala.head.scalaSdk.get)
    module.libraries
    model.commit()
    module
  }

  private def removeIdeaModule() = {
    val model  = ModuleManager.getInstance(project).getModifiableModel
    val module = model.findModuleByName(injectorModuleName)
    if (module != null) {
      model.disposeModule(module)
    } else {
      LOG.warn(s"Failed to remove helper module - $injectorModuleName not found")
    }
  }

  private def withHelperModule[T](f: => T) = {
    createIdeaModule()
    val res = f
    removeIdeaModule()
    res
  }

}
