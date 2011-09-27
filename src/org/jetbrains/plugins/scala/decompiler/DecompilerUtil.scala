package org.jetbrains.plugins.scala
package decompiler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.{VirtualFileWithId, CharsetToolkit, VirtualFile}
import java.io._
import _root_.scala.tools.scalap.scalax.rules.scalasig._
import java.lang.String
import _root_.scala.tools.scalap.scalax.rules.scalasig.ClassFileParser.{ConstValueIndex, Annotation}
import scala.reflect.generic.ByteCodecs
import CharsetToolkit.UTF8
import com.intellij.openapi.project.{DumbServiceImpl, Project, ProjectManager}
import com.intellij.psi.search.GlobalSearchScope

/**
 * @author ilyas
 */
object DecompilerUtil {
  protected val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.decompiler.DecompilerUtil");

  val DECOMPILER_VERSION = 158

  def isScalaFile(file: VirtualFile): Boolean = {
    try {
      isScalaFile(file, file.contentsToByteArray)
    }
    catch {
      case e: IOException => false
    }
  }

  private val IS_SCALA_FILE_KEY = new Key[(java.lang.Boolean, java.lang.Long)]("Is Scala File Key")

  def isScalaFile(file: VirtualFile, bytes: => Array[Byte], fromIndexer: Boolean = false): Boolean = {
    def inner: Boolean = {
      if (file.getFileType != StdFileTypes.CLASS) return false
      if (!file.isInstanceOf[VirtualFileWithId]) return false
      def calc: Boolean = {
        val byteCode = ByteCode(bytes)
        val isPackageObject = file.getName == "package.class"
        try {
          val classFile = ClassFileParser.parse(byteCode)
          val scalaSig = classFile.attribute(SCALA_SIG).map(_.byteCode).map(ScalaSigAttributeParsers.parse).
            getOrElse(null) match {
            // No entries in ScalaSig attribute implies that the signature is stored in the annotation
            case ScalaSig(_, _, entries) if entries.length == 0 => unpickleFromAnnotation(classFile, isPackageObject)
            case other => other
          }
          if (scalaSig == null) false
          else true
        } catch {
          case e => false
        }
      }
      if (fromIndexer || isDumbModeOrUnitTesting) {
        calc
      } else {
        val decompiled = ScalaDecompilerIndex.decompile(file, allProjectsScope)._1
        if (decompiled == ScalaDecompilerIndex.notInScope) calc
        else decompiled != ScalaDecompilerIndex.notScala
      }
    }
    val timeStamp = file.getTimeStamp
    val data = file.getUserData(IS_SCALA_FILE_KEY)
    if (data == null || data._2.longValue() != timeStamp) {
      val res: (java.lang.Boolean, java.lang.Long) = (inner, timeStamp)
      file.putUserData(IS_SCALA_FILE_KEY, res)
      res._1.booleanValue()
    } else data._1.booleanValue()
  }

  private def openedNotDisposedProjects: Array[Project] = {
    val manager = ProjectManager.getInstance
    if (ApplicationManager.getApplication.isUnitTestMode) {
      val testProject = manager.asInstanceOf[ProjectManagerEx].getCurrentTestProject
      if (testProject != null) Array(testProject)
      else Array.empty
    } else {
      manager.getOpenProjects.filter(!_.isDisposed)
    }
  }

  private def allProjectsScope: GlobalSearchScope = {
    val projects = openedNotDisposedProjects
    if (projects.length <= 1) return GlobalSearchScope.allScope(obtainProject)
    var res: GlobalSearchScope = GlobalSearchScope.allScope(projects(0))
    for (i <- 1 until projects.length) res = res.intersectWith(GlobalSearchScope.allScope(projects(i)))
    res
  }

  def obtainProject: Project = {
    val manager = ProjectManager.getInstance
    val projects = openedNotDisposedProjects
    if (projects.length == 0) manager.getDefaultProject
    else projects(0)
  }

  val SOURCE_FILE = "SourceFile"
  val SCALA_SIG = "ScalaSig"
  val SCALA_SIG_ANNOTATION = "Lscala/reflect/ScalaSignature;"
  val BYTES_VALUE = "bytes"

  private def unpickleFromAnnotation(classFile: ClassFile, isPackageObject: Boolean): ScalaSig = {
    import classFile._
    classFile.annotation(SCALA_SIG_ANNOTATION) match {
      case None => null
      case Some(Annotation(_, elements)) =>
        val bytesElem = elements.find(elem => constant(elem.elementNameIndex) == BYTES_VALUE).get
        val bytes = ((bytesElem.elementValue match {
          case ConstValueIndex(index) => constantWrapped(index)
        }).asInstanceOf[StringBytesPair].bytes)
        val length = ByteCodecs.decode(bytes)
        val scalaSig = ScalaSigAttributeParsers.parse(ByteCode(bytes.take(length)))
        scalaSig
    }
  }

  /**
   * @return (Suspension(sourceText), sourceFileName)
   */

  def isDumbModeOrUnitTesting: Boolean = {
    ApplicationManager.getApplication.isUnitTestMode ||
      openedNotDisposedProjects.find(p => DumbServiceImpl.getInstance(p).isDumb) != None
  }

  def decompile(bytes: Array[Byte], file: VirtualFile, fromIndexer: Boolean = false): (String, String) = {
    def calc: (String, String) = {
      val isPackageObject = file.getName == "package.class"
      val byteCode = ByteCode(bytes)
      val classFile = ClassFileParser.parse(byteCode)
      val scalaSig: ScalaSig = classFile.attribute(SCALA_SIG).map(_.byteCode).map(ScalaSigAttributeParsers.parse).get match {
        // No entries in ScalaSig attribute implies that the signature is stored in the annotation
        case ScalaSig(_, _, entries) if entries.length == 0 => unpickleFromAnnotation(classFile, isPackageObject)
        case other => other
      }

      val sourceText = {
        val baos = new ByteArrayOutputStream
        val stream = new PrintStream(baos, true, CharsetToolkit.UTF8)
        if (scalaSig == null) {
          throw new RuntimeException("null scalaSig for file: " + file.getPath)
        }
        val syms = scalaSig.topLevelClasses ::: scalaSig.topLevelObjects
        // Print package with special treatment for package objects
        syms.head.parent match {
          //Partial match
          case Some(p) if (p.name != "<empty>") => {
            val path = p.path
            if (!isPackageObject) {
              stream.print("package ");
              stream.print(path);
              stream.print("\n")
            } else {
              val i = path.lastIndexOf(".")
              if (i > 0) {
                stream.print("package ");
                stream.print(path.substring(0, i))
                stream.print("\n")
              }
            }
          }
          case _ =>
        }

        // Print classes
        val printer = new ScalaSigPrinter(stream, false)

        for (c <- syms) {
          printer.printSymbol(c)
        }
        val sourceBytes = baos.toByteArray
        new String(sourceBytes, UTF8)
      }

      val sourceFileName = {
        val Some(SourceFileInfo(index: Int)) = classFile.attribute(SOURCE_FILE).map(_.byteCode).
          map(SourceFileAttributeParser.parse)
        val c = classFile.header.constants(index)
        val sBytes: Array[Byte] = c match {
          case s: String => s.getBytes(UTF8)
          case scala.tools.scalap.scalax.rules.scalasig.StringBytesPair(s: String, bytes: Array[Byte]) => bytes
          case _ => Array.empty
        }
        new String(sBytes, UTF8)
      }

      (sourceText, sourceFileName)
    }
    if (fromIndexer || isDumbModeOrUnitTesting) {
      calc
    } else {
      val decompile = ScalaDecompilerIndex.decompile(file, allProjectsScope)
      if (decompile._1 == ScalaDecompilerIndex.notInScope) calc
      else decompile
    }
  }
}
