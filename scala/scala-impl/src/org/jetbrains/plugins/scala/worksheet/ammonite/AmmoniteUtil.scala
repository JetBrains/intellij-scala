package org.jetbrains.plugins.scala.worksheet.ammonite

import java.io.File

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.roots.{OrderRootType, ProjectRootManager}
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.containers.ContainerUtilRt
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory}
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
  private val ROOT_PLUGIN = "$plugin"
  
  private val PARENT_FILE = "^"
  
  private val DEFAULT_IMPORTS = Seq("ammonite.main.Router._", "ammonite.runtime.tools.grep", "ammonite.runtime.tools.browse", 
    "ammonite.runtime.tools.time", "ammonite.repl.tools.desugar", "ammonite.repl.tools.source") //todo more default imports ?
  private val DEFAULT_BUILTINS = Seq(("repl", "ammonite.repl.ReplAPI"), ("interp", "ammonite.runtime.Interpreter"))

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
      case Some(q) if scriptResolvePlugin(q) && refElement.getReference.getCanonicalText == ROOT_IVY => //refElement.getReference.getCanonicalText
        Option(refElement.getContainingFile.getContainingDirectory)
      case Some(q) if scriptResolveIvy(q) || scriptResolvePlugin(q) =>
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
        val nv = s"${name}_$scalaVersion"
        
        val ivyPath = s"$group/$nv/jars|bundles/$version.jar"
        val mavenPath = s"maven2/${group.replace('.', '/')}/$nv|$name/$version/$nv-$version.jar|$name-$version.jar"
        
        def tryIvy() = findFileByPattern(
          s"$getDefaultCachePath/$ivyPath", 
          existsPredicate
        )
        
        def tryCoursier() = findFileByPattern(
          s"$getCoursierCachePath/https/repo1.maven.org/$mavenPath",
          existsPredicate
        )
        
        tryIvy() orElse tryCoursier() flatMap { //todo more variants? 
          jarModuleRoot =>
            val jarRoot = JarFileSystem.getInstance().findLocalVirtualFileByPath(jarModuleRoot.getCanonicalPath)
            Option(jarRoot)
        }
    }
  }
  
  def isAmmoniteSpecificImport(expr: ScImportExpr): Boolean = {
    val txt = expr.getText
    txt.startsWith(ROOT_EXEC) || txt.startsWith(ROOT_FILE) || txt.startsWith(ROOT_IVY) || txt.startsWith(ROOT_PLUGIN)
  }
  
  private def findFileByPattern(pattern: String, predicate: File => Boolean): Option[File] = {
    abstract class PathPart[T] {
      protected var current: Option[T] = None
      def getCurrent: Option[T] = current
      def hasNext: Boolean = false

      def add(): Boolean
      def reset()
    }

    case class SimplePart(p: String) extends PathPart[String] {
      reset()
      override def add(): Boolean = {
        current = None
        false
      }
      override def reset(): Unit = current = Option(p)
    }

    case class OrPart(ps: Iterable[String]) extends PathPart[String] {
      private var it = ps.iterator
      setCurrent()

      override def add(): Boolean = {it.hasNext && {setCurrent(); true} || {current = None; false}}
      override def hasNext: Boolean = it.hasNext
      override def reset(): Unit = {
        it = ps.iterator
        setCurrent()
      }

      private def setCurrent() {
        current = Option(it.next())
      }
    }

    case class PathIterator(pathParts: Iterable[PathPart[String]]) extends Iterator[File] {
      private var it = pathParts.iterator
      private var currentDigit = pathParts.head
      private var currentVal: Option[String] = Option(gluePath)

      private def gluePath: String = pathParts.flatMap(_.getCurrent.toList).mkString(File.separator)

      private def advance() {
        if (!currentDigit.add()) {
          while (!currentDigit.add() && it.hasNext) currentDigit = it.next()
          if (currentDigit.getCurrent.isEmpty) return
          pathParts.takeWhile(_ != currentDigit).foreach(_.reset())
          currentDigit = pathParts.head
          it = pathParts.iterator
        }

        currentVal = Option(gluePath)
      }

      def hasNext: Boolean = currentVal.isDefined

      def next(): File = {
        val c = currentVal.get
        currentVal = None
        advance()
        new File(c)
      }
    }
    
    PathIterator {
      pattern.split('/').map {
        part => part.split('|') match {
          case Array(single) => SimplePart(single)
          case multiple => OrPart(multiple)
        }
      }.foldRight(List.empty[PathPart[String]]){
        case (SimplePart(part), SimplePart(pp) :: tail) =>
          SimplePart(part + File.separator + pp) :: tail
        case (otherPart, list) =>
          otherPart :: list
      }
    }.find(predicate)
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
}
