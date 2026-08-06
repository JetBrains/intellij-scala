package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.junit.Assert.assertNotNull

import java.nio.file.Path
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * Loads a module library from local jar paths.
 * Supports one classes jar and an optional sources jar.
 */
final case class LocalJarLibraryLoader(
  libraryName: String,
  classesJarPath: Path,
  sourcesJarPath: Option[Path] = None
) extends LibraryLoader {
  import LocalJarLibraryLoader.LoadedEntities

  private var loadedEntities: Option[LoadedEntities] = None

  def library: Option[Library] = loadedEntities.map(_.library)
  def classesRoot: Option[VirtualFile] = loadedEntities.map(_.classesRoot)
  def sourcesRoot: Option[VirtualFile] = loadedEntities.flatMap(_.sourcesRoot)

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val classesRoot = resolveJarRoot(classesJarPath)
    val sourcesRoots = sourcesJarPath.map(resolveJarRoot).toSeq

    val loadedLibrary = PsiTestUtil.addProjectLibrary(
      module,
      libraryName,
      Seq(classesRoot).asJava,
      sourcesRoots.asJava
    )
    loadedEntities = Some(LoadedEntities(loadedLibrary, classesRoot, sourcesRoots.headOption))
  }

  override def clean(implicit module: Module): Unit = {
    try {
      loadedEntities.foreach { entities =>
        PsiTestUtil.removeLibrary(module, entities.library)
      }
    } finally {
      loadedEntities = None
    }
  }

  private def resolveJarRoot(jarPath: Path): VirtualFile = {
    val jarRoot = JarFileSystem.getInstance.refreshAndFindFileByPath(jarPath.toString + "!/")
    assertNotNull(s"Could not resolve jar root: $jarPath", jarRoot)
    jarRoot
  }
}

object LocalJarLibraryLoader {
  final case class LoadedEntities(
    library: Library,
    classesRoot: VirtualFile,
    sourcesRoot: Option[VirtualFile]
  )
}
