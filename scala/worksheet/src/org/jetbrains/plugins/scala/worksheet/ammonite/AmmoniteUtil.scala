package org.jetbrains.plugins.scala.worksheet.ammonite

import java.io.File
import java.util.regex.Pattern

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.psi.{PsiDirectory, PsiFileSystemItem, PsiManager}
import org.apache.commons.lang.SystemUtils
import org.jetbrains.plugins.scala.editor.importOptimizer.ImportInfo
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.worksheet.{WorksheetFileType, WorksheetUtils}
import org.jetbrains.sbt.project.SbtProjectSystem

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex

/**
 * User: Dmitry.Naydanov
 * Date: 01.08.17.
 */
object AmmoniteUtil {

  val DEFAULT_VERSION = "2.12"

  private val ROOT_FILE   = "$file"
  private val ROOT_EXEC   = "$exec"
  private val ROOT_IVY    = "$ivy"
  private val ROOT_PLUGIN = "$plugin"

  private val PARENT_FILE = "^"

  def isAmmoniteFile(file: ScFile): Boolean =
    ScFile.VirtualFile.unapply(file).exists { virtualFile =>
      WorksheetFileType.isMyFileType(virtualFile) &&
        WorksheetUtils.isAmmoniteEnabled(file.getProject, virtualFile)
    }

  def findAllIvyImports(file: ScalaFile): Seq[LibInfo] = {
    file.getChildren.flatMap {
      case imp: ScImportStmt =>
        imp.importExprs.filter(expr => expr.getText.startsWith(ROOT_IVY) || expr.getText.startsWith(ROOT_PLUGIN)).flatMap(_.reference.flatMap(extractLibInfo))
      case _ => Seq.empty
    }.toSeq
  }

  def file2Object(file: PsiFileSystemItem): Option[ScObject] = file match {
    case scalaFile: ScalaFile => AmmoniteScriptWrappersHolder.getInstance(file.getProject).findWrapper(scalaFile)
    case _ => None
  }

  /** Resolves $file imports */
  def scriptResolveQualifier(refElement: ScStableCodeReference): Option[PsiFileSystemItem] = {
    def scriptResolveNoQualifier(refElement: ScStableCodeReference): Option[PsiDirectory] =
      refElement.getContainingFile match {
        case scalaFile: ScalaFileImpl if isAmmoniteFile(scalaFile) =>
          if (refElement.textMatches(ROOT_FILE) || refElement.textMatches(ROOT_EXEC)) {
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
              case other =>
                d match {
                  case dir: PsiDirectory =>
                    Option(dir.findFile(other + "." + WorksheetFileType.getDefaultExtension)).orElse {
                      Option(dir.findSubdirectory(other))
                    }
                  case _ => None
                }
            }
          case a@None => a
        }
      case None =>
        scriptResolveNoQualifier(refElement)
    }
  }

  def scriptResolveSbtDependency(refElement: ScStableCodeReference): Option[PsiDirectory] = {
    def scriptResolveIvy(refElement: ScStableCodeReference) = refElement.textMatches(ROOT_IVY)
    def scriptResolvePlugin(refElement: ScStableCodeReference) = refElement.textMatches(ROOT_PLUGIN)

    def qual(scRef: ScStableCodeReference) = {
      scRef.getParent match {
        case selector: ScImportSelector =>
          new ParentsIterator(selector).collectFirst {
            case ScImportExpr.qualifier(qualifier) => qualifier
          }
        case _ => scRef.qualifier
      }
    }

    qual(refElement) match {
      case Some(q) if scriptResolvePlugin(q) && refElement.getReference.getCanonicalText == ROOT_IVY =>
        Option(refElement.getContainingFile.getContainingDirectory)
      case Some(q) if scriptResolveIvy(q) || scriptResolvePlugin(q) || q.getReference.refName == ROOT_IVY =>
        findLibrary(refElement) flatMap {
          lib => getResolveItem(lib, refElement.getProject)
        }
      case None if scriptResolveIvy(refElement) || scriptResolvePlugin(refElement) =>
        Option(refElement.getContainingFile.getContainingDirectory)
      case _ => None
    }
  }

  def findJarRoot(refElement: ScReference): Option[VirtualFile] = {
    AmmoniteUtil.extractLibInfo(refElement).flatMap {
      case LibInfo(group, name, version, scalaVersion) =>
        val existsPredicate = (f: File) => f.exists()

        val n = name
        val nv = s"${name}_$scalaVersion"
        val fullVersion = {
          val magicValue = ScalaUtil.getScalaVersion(refElement.getContainingFile).flatMap(_.split('.').lastOption)
          s"$n|$nv|$nv.${magicValue.getOrElse("0")}"
        }

        val ivyPath = s"$group/$fullVersion/jars|bundles"
        val mavenPath = s"${group.replace('.', '/')}/$nv|$name/$version"

        val prefixPatterns = Seq(name, version)

        def tryIvy(): Option[File] =
          firstFileMatchingPattern(s"/$ivyPath", new File(getDefaultCachePath))
            .find(existsPredicate)

        def tryCoursier(): Option[File] =
          firstFileMatchingPattern(s"/https/*/*/$mavenPath", new File(getCoursierCachePath))
            .find(existsPredicate)

        val fileOpt = tryIvy().orElse(tryCoursier())
        for {
          parent <- fileOpt
          files = parent.listFiles()
          jarModuleRoot <- files.find { cf =>
            val name = cf.getName
            prefixPatterns.exists(name.startsWith) &&
              name.endsWith(".jar") &&
              !name.endsWith("-sources.jar") &&
              !name.endsWith("-javadoc.jar")
          }
          res <-  Option(JarFileSystem.getInstance().findLocalVirtualFileByPath(jarModuleRoot.getCanonicalPath))
        } yield res
    }
  }

  private def isAmmoniteRefText(txt: String): Boolean =
    txt.startsWith(ROOT_EXEC) || txt.startsWith(ROOT_FILE) || txt.startsWith(ROOT_IVY) || txt.startsWith(ROOT_PLUGIN)

  def isAmmoniteSpecificTextImport(expr: ScImportExpr): Boolean = isAmmoniteRefText(expr.getText)

  def isAmmoniteSpecificImport(imp: ImportInfo): Boolean = isAmmoniteRefText(imp.prefixQualifier)

  def isAmmoniteSpecificImport(expr: ScImportExpr): Boolean = expr.getContainingFile match {
    case scalaFile: ScalaFile if isAmmoniteFile(scalaFile) => isAmmoniteSpecificTextImport(expr)
    case _                                                 => false
  }

  def isAmmoniteSpecificImport(imp: ImportUsed): Boolean = imp.element match {
    case expr: ScImportExpr => isAmmoniteSpecificImport(expr)
    case selector: ScImportSelector =>
      selector.getContext match {
        case selectors: ScImportSelectors => selectors.getContext match {
          case expr: ScImportExpr => return isAmmoniteSpecificImport(expr)
          case _ =>
        }
        case _ =>
      }

      false
    case _ => false
  }

  sealed trait FileTree
  final case class OneSegment(segment: String, next: FileTree) extends FileTree
  final case class AlternativeSegments(segments: List[String], next: FileTree)
      extends FileTree
  final case class AnySegment(next: FileTree) extends FileTree
  final case object Empty extends FileTree

  private def segment(s: String, down: FileTree) = s.split('|').toList match {
    case "*" :: Nil => AnySegment(down)
    case str :: Nil => OneSegment(str, down)
    case array      => AlternativeSegments(array, down)
  }

  def patternToTree(pattern: String): FileTree =
    pattern.split("/").filter(_.nonEmpty).foldRight[FileTree](Empty)(segment)

  @tailrec
  def treeToFiles(tree: FileTree, acc: List[File]): List[File] = tree match {
    case Empty => acc
    case OneSegment(segment, next) =>
      treeToFiles(next, acc.filter(_.isDirectory).flatMap(_.listFiles().filter(_.getName == segment)))
    case AlternativeSegments(segments, next) =>
      treeToFiles(next, acc.filter(_.isDirectory).flatMap(_.listFiles().filter(f => segments.contains(f.getName))))
    case AnySegment(next) =>
      treeToFiles(next, acc.filter(_.isDirectory).flatMap(_.listFiles()))
  }

  def firstFileMatchingPattern(pattern: String, rootFile: File): List[File] =
    treeToFiles(patternToTree(pattern), List(rootFile))

  private def getResolveItem(library: Library,
                             project: Project): Option[PsiDirectory] =
    getLibraryDirs(library, project).headOption

  private def getLibraryDirs(library: Library,
                             project: Project): Array[PsiDirectory] =
    library.getFiles(OrderRootType.CLASSES).flatMap { root =>
      Option(PsiManager.getInstance(project).findDirectory(root))
    }

  private def findLibrary(refElement: ScStableCodeReference): Option[Library] =
    extractLibInfo(refElement).map(convertLibName) flatMap { name =>
      Option(
        LibraryTablesRegistrar
          .getInstance() getLibraryTable refElement.getProject getLibraryByName name)
    }

  def convertLibName(info: LibInfo): String =
    List(SbtProjectSystem.Id.getId, " " + info.groupId, info.name + "_" + info.scalaVersion, info.version, "jar").mkString(":")

  private def getScalaVersion(element: ScalaPsiElement): String =
    ScalaUtil.getScalaVersion(element.getContainingFile)
      .map{ version =>
        version.lastIndexOf('.') match {
          case a if a < 2 => version
          case i          => version.substring(0, i)
        }
      }
      .getOrElse(DEFAULT_VERSION)

  case class LibInfo(groupId: String, name: String, version: String, scalaVersion: String)

  def extractLibInfo(ref: ScReference): Option[LibInfo] = {
    val name = ref.refName.stripPrefix("`").stripSuffix("`")
    val result = ArrayBuffer[String]()

    var scalaVersion: Option[String] = None

    name.split(':').foreach { p =>
      if (p.nonEmpty) {
        val prefix = if (p.contains("_")) {
          p.split('_') match {
            case Array(prefix, suffix@("2.10" | "2.11" | "2.12")) =>
              scalaVersion = Option(suffix)
              prefix
            case _ => p
          }
        } else p

        result += prefix
      }
    }

    if (result.length == 3)
      Some(LibInfo(result.head, result(1), result(2), scalaVersion getOrElse getScalaVersion(ref)))
    else
      None
  }

  def getDefaultCachePath: String = System.getProperty("user.home") + "/.ivy2/cache"

  def getCoursierCachePath: String = System.getProperty("user.home") + {
    if (SystemUtils.IS_OS_MAC) "/Library/Caches/Coursier/v1"
    else if (SystemUtils.IS_OS_WINDOWS) "\\AppData\\Local\\Coursier\\Cache\\v1"
    else "/.coursier/cache/v1"
  }

  class RegexExtractor {
    private val patternCache = mutable.HashMap[String, Regex]()

    implicit class MyStringExtractorContext(private val sc: StringContext) {

      object mre {
        def apply(args: Any*): String = sc.s(args: _*)

        def unapplySeq(s: String): Option[Seq[String]] = {
          val patternString = sc.parts.map(Pattern.quote).mkString("(.+)")
          val regex = patternCache.getOrElseUpdate(patternString, patternString.r)
          regex.unapplySeq(s)
        }
      }
    }
  }
}
