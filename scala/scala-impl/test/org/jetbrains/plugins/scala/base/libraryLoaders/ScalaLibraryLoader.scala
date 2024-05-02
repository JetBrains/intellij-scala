package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.Resolver
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.{DependencyManager, DependencyManagerBase, ScalaVersion}

import java.io.File
import java.{util => ju}

/**
 * The loader loads and registers only nscala library (with sources) without transitive dependencies
 * It doesn't load compiler classpath jars and creates a simple library
 */
final case class ScalaLibraryLoader(
  scalaVersion: ScalaVersion,
  dependencyManager: DependencyManagerBase = DependencyManager
)
  extends LibraryLoader {

  import DependencyManagerBase._
  import ScalaLibraryLoader.findJarFile

  //NOTE: we ignore implicitly passed ScalaVersion and use version explicitly set in the parameters
  override def init(implicit module: Module, ignored: ScalaVersion): Unit = {
    initImpl(module)
  }

  private def initImpl(module: Module): Unit = {
    import scala.jdk.CollectionConverters._

    implicit val scalaVersionImplicit: ScalaVersion = scalaVersion

    val scalaLibraryClasses: ju.List[VirtualFile] = {
      val files: Seq[File] = dependencyManager.resolve(scalaLibraryDescription).map(_.file)
      files.map(findJarFile).asJava
    }
    val scalaLibrarySources: ju.List[VirtualFile] = {
      val files = dependencyManager.resolve(scalaLibraryDescription % Types.SRC).map(_.file)
      files.map(findJarFile).asJava
    }

    val libraryTable = LibraryTablesRegistrar.getInstance.getLibraryTable(module.getProject)
    val scalaLibraryName = s"scala-library-${scalaVersion.minor}"

    def createNewLibrary: Library =
      PsiTestUtil.addProjectLibrary(
        module,
        scalaLibraryName,
        scalaLibraryClasses,
        scalaLibrarySources
      )

    val existingLibrary = Option(libraryTable.getLibraryByName(scalaLibraryName))
    existingLibrary.getOrElse(createNewLibrary)
  }
}

object ScalaLibraryLoader {

  private def findJarFile(file: File) =
    JarFileSystem.getInstance().refreshAndFindFileByPath {
      file.getCanonicalPath + "!/"
    }

  /**
   * This utility "overrides" default scala sdk loader. use non-standard resolvers
   * It uses separate scala libraries with specified versions
   */
  def libraryLoadersWithSeparateScalaLibraries(
    superLibraryLoaders: Seq[LibraryLoader],
    scala2Version: ScalaVersion,
    scala3Version: ScalaVersion,
  ): Seq[LibraryLoader] = {
    val scala2LibraryLoader = ScalaLibraryLoader(scala2Version)
    val scala3LibraryLoader = ScalaLibraryLoader(scala3Version)

    //We use resolveScalaLibraryTransitiveDependencies = false in order to use the latest 2.13.14 RC version
    val scala3SdkLoader = ScalaSDKLoader(includeLibraryFilesInSdk = false)

    Seq(
      scala3LibraryLoader,
      scala2LibraryLoader,
      scala3SdkLoader
    ) ++ superLibraryLoaders.filterNot(_.is[ScalaSDKLoader])
  }
}