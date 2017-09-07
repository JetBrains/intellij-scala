package org.jetbrains.plugins.scala.worksheet.ammonite

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.roots.{OrderRootType, ProjectRootManager}
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.psi._
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.sbt.project.SbtProjectSystem

import scala.collection.mutable.ArrayBuffer

/**
  * User: Dmitry.Naydanov
  * Date: 01.08.17.
  */
object AmmoniteUtil {
  val AMMONITE_EXTENSION = "sc"
  
  private val DEFAULT_VERSION = "2.12"
  private val ROOT_FILE = "$file"
  private val ROOT_EXEC = "$exec"
  private val ROOT_IVY = "$ivy"
  private val PARENT_FILE = "^"

  def isAmmoniteFile(file: ScalaFile): Boolean = 
    ScalaProjectSettings.getInstance(file.getProject).isTreatScAsAmmonite && 
      ScalaUtil.findVirtualFile(file).exists(_.getExtension == AMMONITE_EXTENSION)

  def findAllIvyImports(file: ScalaFile): Array[String] = {
    file.getChildren.flatMap {
      case imp: ScImportStmt =>
        imp.importExprs.filter(_.getText.startsWith(ROOT_IVY)).flatMap(_.reference.map(_.refName))
      case _ => Seq.empty
    }
  }

  def file2Object(file: PsiFileSystemItem): Option[ScObject] = file match {
    case scalaFile: ScalaFile => AmmoniteScriptWrappersHolder.getInstance(file.getProject).findWrapper(scalaFile)
    case _ => None
  }

  /*
  Resolves $file imports
   */
  def scriptResolveQualifier(refElement: ScStableCodeReferenceElement): Option[PsiFileSystemItem] = {
    def scriptResolveNoQualifier(refElement: ScStableCodeReferenceElement): Option[PsiDirectory] =
      refElement.getContainingFile match {
        case scalaFile: ScalaFileImpl if isAmmoniteFile(scalaFile) =>
          if (refElement.getText == ROOT_FILE || refElement.getText == ROOT_EXEC) {
            val dir = scalaFile.getContainingDirectory

            if (dir != null) Option(scalaFile.getContainingDirectory) else {
              Option(scalaFile.getOriginalFile).flatMap(file => Option(file.getContainingDirectory))
            }
          } else None
        case _ => None
      }

    refElement.qualifier match {
      case Some(q) =>
        scriptResolveQualifier(q) match {
          case Some(d) =>
            refElement.refName match {
              case PARENT_FILE => Option(d.getParent)
              case other if d.isDirectory =>
                Option(d.asInstanceOf[PsiDirectory].findFile(s"$other.$AMMONITE_EXTENSION"))
              case _ => None
            }
          case a@None => a
        }
      case None => scriptResolveNoQualifier(refElement)
    }
  }

  def scriptResolveSbtDependency(refElement: ScStableCodeReferenceElement): Option[PsiDirectory] = {
    def scriptResolveIvy(refElement: ScStableCodeReferenceElement) = refElement.getText == ROOT_IVY

    refElement.qualifier match {
      case Some(q) if scriptResolveIvy(q) =>
        findLibrary(refElement) flatMap {
          lib => getResolveItem(lib, refElement.getProject)
        }
      case None if scriptResolveIvy(refElement) =>
        Option(refElement.getContainingFile.getContainingDirectory)
      case _ => None
    }
  }

  def findJarRoot(refElement: ScReferenceElement): Option[VirtualFile] = {
    AmmoniteUtil.extractLibInfo(refElement).flatMap {
      case LibInfo(group, name, version, scalaVersion) =>
        val root = {
          val r = new File(getDefaultCachePath, s"$group${File.separator}${name}_$scalaVersion")
          val f = new File(r, "jars")
          if (f.exists()) f else new File(r, "bundles")
        }

        val suffix = s"$version.jar"

        if (root.exists() && root.isDirectory) root.listFiles().find(_.getName.endsWith(suffix)).flatMap {
          jarModuleRoot =>
            val jarRoot = JarFileSystem.getInstance().findLocalVirtualFileByPath(jarModuleRoot.getCanonicalPath)
            Option(jarRoot)
        } else None
    }
  }

  private def getResolveItem(library: Library, project: Project): Option[PsiDirectory] = getLibraryDirs(library, project).headOption

  private def getLibraryDirs(library: Library, project: Project): Array[PsiDirectory] = {
    library.getFiles(OrderRootType.CLASSES).flatMap {
      root => Option(PsiManager.getInstance(project).findDirectory(root))
    }
  }

  private def findLibrary(refElement: ScStableCodeReferenceElement): Option[Library] = {
    extractLibInfo(refElement).map(convertLibName) flatMap {
      name =>
        Option(LibraryTablesRegistrar.getInstance() getLibraryTable refElement.getProject getLibraryByName name)
    }
  }

  def convertLibName(info: LibInfo): String = {
    import info._
    
    List(SbtProjectSystem.Id.getId, " " + groupId, name + "_" + scalaVersion, version, "jar").mkString(":")
  }
  
  private def getScalaVersion(element: ScalaPsiElement): String = {
    Option(PsiUtilCore getVirtualFile element).flatMap {
      file => getModuleForFile(file, element.getProject)
    } flatMap {
      module => module.scalaSdk
    } flatMap {
      sdk => sdk.compilerVersion
    } map {
      version => version.lastIndexOf('.') match {
        case a if a < 2 => version
        case i => version.substring(0, i)
      }
    } getOrElse DEFAULT_VERSION
  }

  case class LibInfo(groupId: String, name: String, version: String, scalaVersion: String)
  
  def extractLibInfo(ref: ScReferenceElement): Option[LibInfo] = {
    val name = ref.refName.stripPrefix("`").stripSuffix("`")
    val result = ArrayBuffer[String]()

    name.split(':').foreach {
      p => if (p.nonEmpty) result += p
    }

    if (result.length == 3) Some(LibInfo(result.head, result(1), result(2), getScalaVersion(ref))) else None 
  }

  def getDefaultCachePath: String = System.getProperty("user.home") + "/.ivy2/cache".replace('/', File.separatorChar)
  
  def getModuleForFile(virtualFile: VirtualFile, project: Project): Option[Module] =
    Option(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))
}
