package org.jetbrains.plugins.scala.worksheet.ammonite

import java.io.File
import java.util.regex.Pattern

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.roots.{OrderRootType, ProjectRootManager}
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.containers.ContainerUtilRt
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.editor.importOptimizer.ImportInfo
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil
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
  val AMMONITE_EXTENSION = "sc"
  
  val DEFAULT_VERSION = "2.12"
  private val ROOT_FILE = "$file"
  private val ROOT_EXEC = "$exec"
  private val ROOT_IVY = "$ivy"
  private val ROOT_PLUGIN = "$plugin"

  private val PARENT_FILE = "^"

  private val DEFAULT_IMPORTS = Seq("ammonite.main.Router._", "ammonite.runtime.tools.grep", "ammonite.runtime.tools.browse",
    "ammonite.runtime.tools.time", "ammonite.repl.tools.desugar", "ammonite.repl.tools.source") //todo more default imports ?
  private val DEFAULT_BUILTINS = Seq(("repl", "ammonite.repl.ReplAPI"), ("interp", "ammonite.runtime.Interpreter with ammonite.interp.Interpreter"))

  def isAmmoniteFile(file: ScalaFile): Boolean = {
    ScalaUtil.findVirtualFile(file) match {
      case Some(vFile) => isAmmoniteFile(vFile, file.getProject)
      case _ => false
    }
  }

  def isAmmoniteFile(virtualFile: VirtualFile, project: Project): Boolean = {
    virtualFile.getExtension == AMMONITE_EXTENSION && (ScalaProjectSettings.getInstance(project).getScFileMode match {
      case ScalaProjectSettings.ScFileMode.Ammonite => true
      case ScalaProjectSettings.ScFileMode.Worksheet => false
      case ScalaProjectSettings.ScFileMode.Auto =>
        ProjectRootManager.getInstance(project).getFileIndex.isUnderSourceRootOfType(virtualFile, ContainerUtilRt.newHashSet(JavaSourceRootType.TEST_SOURCE))
      case _ => false
    })
  }

  def findAllIvyImports(file: ScalaFile): Seq[LibInfo] = {
    file.getChildren.flatMap {
      case imp: ScImportStmt =>
        imp.importExprs.filter(expr => expr.getText.startsWith(ROOT_IVY) || expr.getText.startsWith(ROOT_PLUGIN)).flatMap(_.reference.flatMap(extractLibInfo))
      case _ => Seq.empty
    }
  }

  def file2Object(file: PsiFileSystemItem): Option[ScObject] = file match {
    case scalaFile: ScalaFile => AmmoniteScriptWrappersHolder.getInstance(file.getProject).findWrapper(scalaFile)
    case _ => None
  }

  def executeImplicitImportsDeclarations(processor: PsiScopeProcessor, file: FileDeclarationsHolder, state: ResolveState): Boolean = {
    file match {
      case ammoniteFile: ScalaFile if isAmmoniteFile(ammoniteFile) =>
        DEFAULT_BUILTINS.foreach {
          case (name, txt) =>
            ScalaPsiElementFactory.createElementFromText(s"class A { val $name: $txt = ??? }")(ammoniteFile.projectContext).processDeclarations(processor, state, null, ammoniteFile)
        }

        DEFAULT_IMPORTS.foreach {
          imp =>
            val importStmt = ScalaPsiElementFactory.createImportFromText(s"import $imp")(ammoniteFile.projectContext)
            importStmt.processDeclarations(processor, state, null, ammoniteFile)
        }
      case _ =>
    }

    true
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
              case other =>
                d match {
                  case dir: PsiDirectory =>
                    Option(dir findFile s"$other.$AMMONITE_EXTENSION").orElse(Option(dir findSubdirectory other))
                  case _ => None
                }
            }
          case a@None => a
        }
      case None => scriptResolveNoQualifier(refElement)
    }
  }

  def scriptResolveSbtDependency(refElement: ScStableCodeReferenceElement): Option[PsiDirectory] = {
    def scriptResolveIvy(refElement: ScStableCodeReferenceElement) = refElement.getText == ROOT_IVY
    def scriptResolvePlugin(refElement: ScStableCodeReferenceElement) = refElement.getText == ROOT_PLUGIN

    def qual(scRef: ScStableCodeReferenceElement) = {
      scRef.getParent match {
        case selector: ScImportSelector =>
          new ParentsIterator(selector).collectFirst {
            case expr: ScImportExpr => expr.qualifier
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

  def findJarRoot(refElement: ScReferenceElement): Option[VirtualFile] = {
    AmmoniteUtil.extractLibInfo(refElement).flatMap {
      case LibInfo(group, name, version, scalaVersion) =>
        val existsPredicate = (f: File) => f.exists()

        val n = name
        val nv = s"${name}_$scalaVersion"
        val fullVersion =
          s"$n|$nv|$nv.${ScalaUtil.getScalaVersion(refElement.getContainingFile).flatMap(_.split('.').lastOption).getOrElse("0")}"

        val ivyPath = s"$group/$fullVersion/jars|bundles"
        val mavenPath = s"${group.replace('.', '/')}/$nv|$name/$version"

        val prefixPatterns = Seq(name, version)

        def tryIvy() =
          firstFileMatchingPattern(s"/$ivyPath", new File(getDefaultCachePath))
            .find(existsPredicate)

        def tryCoursier() =
          firstFileMatchingPattern(s"/https/*/*/$mavenPath", new File(getCoursierCachePath))
            .find(existsPredicate)

        tryIvy() orElse tryCoursier() flatMap { parent =>
          parent.listFiles().find { cf =>
            val name = cf.getName
            prefixPatterns.exists(name.startsWith) &&
            name.endsWith(".jar") &&
            !name.endsWith("-sources.jar") &&
            !name.endsWith("-javadoc.jar")
          } flatMap { //todo more variants?
            jarModuleRoot =>
              Option(
                JarFileSystem
                  .getInstance()
                  .findLocalVirtualFileByPath(jarModuleRoot.getCanonicalPath))
          }
        }
    }
  }

  private def isAmmonteRefText(txt: String): Boolean =
    txt.startsWith(ROOT_EXEC) || txt.startsWith(ROOT_FILE) || txt.startsWith(ROOT_IVY) || txt.startsWith(ROOT_PLUGIN)
  
  def isAmmoniteSpecificTextImport(expr: ScImportExpr): Boolean = isAmmonteRefText(expr.getText)
  
  def isAmmoniteSpecificImport(imp: ImportInfo): Boolean = isAmmonteRefText(imp.prefixQualifier)
  
  def isAmmoniteSpecificImport(expr: ScImportExpr): Boolean = expr.getContainingFile match {
    case scalaFile: ScalaFile if isAmmoniteFile(scalaFile) => isAmmoniteSpecificTextImport(expr)
    case _ => false
  }
  
  def isAmmoniteSpecificImport(imp: ImportUsed): Boolean = imp.e match {
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
  
  def processAmmoniteImportUsed(imp: ScImportExpr, importsUsed: ArrayBuffer[ImportUsed]) {
    if (isAmmoniteSpecificImport(imp)) importsUsed += ImportExprUsed(imp)
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
                             project: Project): Array[PsiDirectory] = {
    library.getFiles(OrderRootType.CLASSES).flatMap { root =>
      Option(PsiManager.getInstance(project).findDirectory(root))
    }
  }

  private def findLibrary(
      refElement: ScStableCodeReferenceElement): Option[Library] = {
    extractLibInfo(refElement).map(convertLibName) flatMap { name =>
      Option(
        LibraryTablesRegistrar
          .getInstance() getLibraryTable refElement.getProject getLibraryByName name)
    }
  }

  def convertLibName(info: LibInfo): String = {
    import info._

    List(SbtProjectSystem.Id.getId, " " + groupId, name + "_" + scalaVersion, version, "jar").mkString(":")
  }

  private def getScalaVersion(element: ScalaPsiElement): String = {
    ScalaUtil.getScalaVersion(element.getContainingFile) map {
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

    var scalaVersion: Option[String] = None

    name.split(':').foreach {
      p => if (p.nonEmpty) {
        if (p contains "_") {
          p.split('_') match {
            case Array(prefix, suffix@("2.10" | "2.11" | "2.12")) =>
              scalaVersion = Option(suffix)
              result += prefix
            case _ => result += p
          }
        } else result += p
      }
    }

    if (result.length == 3) Some(LibInfo(result.head, result(1), result(2), scalaVersion getOrElse getScalaVersion(ref))) else None
  }

  def getDefaultCachePath: String = System.getProperty("user.home") + "/.ivy2/cache"

  def getCoursierCachePath: String = System.getProperty("user.home") + "/.coursier/cache/v1"
  
  class RegexExtractor {
    private val patternCache = mutable.HashMap[String, Regex]()

    implicit class MyStringExtractorContext(val sc: StringContext) {
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
