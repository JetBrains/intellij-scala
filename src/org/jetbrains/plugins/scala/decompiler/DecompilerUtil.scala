package org.jetbrains.plugins.scala
package decompiler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFileWithId, CharsetToolkit, VirtualFile}
import java.io._
import tools.scalap.scalax.rules.scalasig._
import java.lang.String
import tools.scalap.scalax.rules.scalasig.ClassFileParser.{ConstValueIndex, Annotation}
import scala.reflect.generic.ByteCodecs
import org.jetbrains.plugins.scala.Suspension
import CharsetToolkit.UTF8

/**
 * @author ilyas
 */
object DecompilerUtil {
  protected val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.decompiler.DecompilerUtil");

  val DECOMPILER_VERSION = 133
  private val isScalaCompiledAttribute = new FileAttribute("_is_scala_compiled_", DECOMPILER_VERSION, true)

  def isScalaFile(file: VirtualFile): Boolean = try {
    isScalaFile(file, file.contentsToByteArray)
  }
  catch {
    case e: IOException => false
  }

  private val SCALA_FILE = new Key[java.lang.Boolean]("Is Scala File Key")

  def isScalaFile(file: VirtualFile, bytes: => Array[Byte]): Boolean = {
    def inner: Boolean = {
      if (file.getFileType != StdFileTypes.CLASS) return false
      if (!file.isInstanceOf[VirtualFileWithId]) return false
      //todo: needs update on file change (getTimeStamp?)
      val read = isScalaCompiledAttribute.readAttribute(file)
      if (read != null) try {read.readBoolean} finally {read.close()} else {
        val byteCode = ByteCode(bytes)
        val isPackageObject = file.getName == "package.class"
        val isScala = try {
          val classFile = ClassFileParser.parse(byteCode)
          val scalaSig = classFile.attribute(SCALA_SIG).map(_.byteCode).map(ScalaSigAttributeParsers.parse).
            getOrElse(null) match {
            // No entries in ScalaSig attribute implies that the signature is stored in the annotation
            case ScalaSig(_, _, entries) if entries.length == 0 => unpickleFromAnnotation(classFile, isPackageObject)
            case scalaSig => scalaSig
          }
          if (scalaSig == null) false
          else true
        } catch {
          case e => false
        }
        val write = isScalaCompiledAttribute.writeAttribute(file)
        write.writeBoolean(isScala)
        write.close()
        isScala
      }
    }
    val b = file.getUserData(SCALA_FILE)
    if (b != null) return java.lang.Boolean.TRUE == b
    val res = inner
    file.putUserData(SCALA_FILE, new java.lang.Boolean(res))
    res
  }

  def obtainProject: Project = {
    val manager = ProjectManager.getInstance
    if (ApplicationManager.getApplication.isUnitTestMode) {
      manager.asInstanceOf[ProjectManagerEx].getCurrentTestProject
    } else {
      val projects = manager.getOpenProjects
      if (projects.length == 0) manager.getDefaultProject
      else projects.find(p => !p.isDisposed).getOrElse(manager.getDefaultProject)
    }
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
  def decompile(bytes: Array[Byte], file: VirtualFile): (Suspension[String], String) = {

    val isPackageObject = file.getName == "package.class"
    val byteCode = ByteCode(bytes)
    val classFile = ClassFileParser.parse(byteCode)
    val scalaSig: ScalaSig = classFile.attribute(SCALA_SIG).map(_.byteCode).map(ScalaSigAttributeParsers.parse).get match {
      // No entries in ScalaSig attribute implies that the signature is stored in the annotation
      case ScalaSig(_, _, entries) if entries.length == 0 => unpickleFromAnnotation(classFile, isPackageObject)
      case scalaSig => scalaSig
    }

    // Lazy so that ScalaFileImpl#sourceName, called frequently during debugging, is fast. See SCL-2852
    lazy val sourceText = {
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
      val Some(SourceFileInfo(index)) = classFile.attribute(SOURCE_FILE).map(_.byteCode).map(SourceFileAttributeParser.parse)
      val c = classFile.header.constants(index)
      val sBytes: Array[Byte] = c match {
        case s: String => s.getBytes(UTF8)
        case scala.tools.scalap.scalax.rules.scalasig.StringBytesPair(s: String, bytes: Array[Byte]) => bytes
        case _ => Array.empty
      }
      new String(sBytes, UTF8)
    }

    (new Suspension(() => sourceText), sourceFileName)
  }
}
