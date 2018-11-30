package org.jetbrains.plugins.scala.components.libextensions

import java.io.{File, InputStreamReader}
import java.util.Collections

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.util.download.DownloadableFileService
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, IvyResolver, MavenResolver}
import org.jetbrains.plugins.scala.components.libextensions.LibraryExtensionsManager._
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.using
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}

class ExtensionDownloader(private val progress: ProgressIndicator, private val sbtResolvers: Set[SbtResolver])(implicit project: Project) {

  private val LOG         = Logger.getInstance(classOf[ExtensionDownloader])

  def getExtensionJars: Seq[File] = {
    val jarsWithProps = findJarsWithProps()
    for {(jar, prop) <- jarsWithProps} {
      LOG.info(s"${jar.getPath} -> ${prop.artifact}")
    }
    val libraryProps = jarsWithProps.map(_._2).toSet
    val bundledProps = getPropsFromLocalIndex
    val jars  = getRemoteExtensions((libraryProps ++ bundledProps).toSeq)
    jars
  }

  private def getPropsFromLocalIndex: Seq[ExtensionProps] = {
    val table = ProjectLibraryTable.getInstance(project)
    val descriptions = table.getLibraries.flatMap { lib =>
      lib.getName.split(": ?") match {
        case Array("sbt", org, module, version, "jar") =>
          Some(DependencyDescription(org, module, version))
        case _ =>
          None
      }
    }
    descriptions.flatMap(BundledExtensionIndex.INDEX.get)
  }

  private def getRemoteExtensions(props: Seq[ExtensionProps]): Seq[File] = {
    val (normal, withOverrides) = props.partition(_.urlOverride == null)
    downloadViaIvy(normal) ++ withOverrides.flatMap(downloadDirect)
  }

  private def downloadDirect(props: ExtensionProps): Option[File] = {
    import scala.collection.convert.ImplicitConversionsToScala._

    val downloadRoot = new File(PathManager.getSystemPath, "scala/extensionsCache")
    val fileName     = s"${Math.abs(props.hashCode())}.jar"
    val targetFile   = new File(downloadRoot, fileName)
    if (targetFile.exists() && targetFile.length() > 0)
      return Some(targetFile)

    val fileService = DownloadableFileService.getInstance()
    val description = fileService.createFileDescription(props.urlOverride, s"$fileName.part")
    val downloader  = fileService.createDownloader(Collections.singletonList(description), props.urlOverride)

    progress.setText(s"Downloading ${props.urlOverride}")
    val files = downloader.download(downloadRoot)
    if (files == null || files.isEmpty) {
      LOG.error(s"Failed to download extension from ${props.urlOverride}")
      targetFile.delete()
      None
    } else {
      files
        .headOption
        .map { result =>
          val partFile = result.first
          if  (partFile.exists())
            partFile.renameTo(targetFile)
          partFile
        }
    }
  }

  private def downloadViaIvy(props: Seq[ExtensionProps]): Seq[File] = {
    val ivyResolvers = sbtResolvers.toSeq.collect {
      case r: SbtMavenResolver => MavenResolver(r.name, r.root)
      case r: SbtIvyResolver if r.name != "Local cache" => IvyResolver(r.name, r.root)
    }
    val deps = props.map(_.artifact.toDepDescription)
    val resolver = new IvyExtensionsResolver(ivyResolvers, progress)
    resolver.resolve(deps:_*).map(_.file)
  }

  private def findJarsWithProps(): Seq[(VirtualFile, ExtensionProps)] = {
    val jarFS = JarFileSystem.getInstance
    val files = extensions.inReadAction {
      FilenameIndex.getFilesByName(project, PROPS_NAME, GlobalSearchScope.allScope(project))
    }
    val containingJars = files.collect {
      case f if f.getVirtualFile != null && f.getVirtualFile.getFileSystem == jarFS =>
        jarFS.getJarRootForLocalFile(jarFS.getVirtualFileForJar(f.getVirtualFile))
    }
    val props = containingJars.map(extractPropsFromJar)
    containingJars.zip(props).collect { case (file, Some(p)) => file -> p }
  }

  private def extractPropsFromJar(jarFile: VirtualFile): Option[ExtensionProps] = {
    import com.google.gson._
    val propsVF = jarFile.findFileByRelativePath(s"META-INF/$PROPS_NAME")
    Option(propsVF)
      .flatMap(x =>
        using(new InputStreamReader(x.getInputStream)) { reader =>
          val props = new Gson().fromJson(reader, classOf[ExtensionProps])
          validateProps(props) match {
            case Left(error)   =>
              LOG.error(error + s"\nFrom file: $jarFile")
              None
            case Right(result) => Some(result)
          }
        }
      )
  }

  private def validateProps(props: ExtensionProps): Either[String, ExtensionProps] = {
    if ((props.artifact == null || props.artifact.isEmpty) && (props.urlOverride == null || props.urlOverride.isEmpty))
      return Left("Extension artifact and extension jar URL are not defined")
    if (props.artifact != null && props.artifact.count(_ == '%') < 2)
      return Left(s"Extension props is malformed: ${props.artifact}")
    Right(props)
  }

}
