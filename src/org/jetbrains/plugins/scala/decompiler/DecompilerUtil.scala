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
import scala.reflect.internal.pickling.ByteCodecs
import CharsetToolkit.UTF8
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.reference.SoftReference

/**
 * @author ilyas
 */
object DecompilerUtil {
  protected val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.decompiler.DecompilerUtil")
  val DECOMPILER_VERSION = 250
  private val SCALA_DECOMPILER_FILE_ATTRIBUTE = new FileAttribute("_is_scala_compiled_", DECOMPILER_VERSION, true)
  private val SCALA_DECOMPILER_KEY = new Key[SoftReference[DecompilationResult]]("Is Scala File Key")
  
  case class DecompilationResult(isScala: Boolean, sourceName: String, sourceText: String, timeStamp: Long)
  object DecompilationResult {
    def empty: DecompilationResult = DecompilationResult(isScala = false, sourceName = "", sourceText = "", timeStamp = 0L)
  }

  private def openedNotDisposedProjects: Array[Project] = {
    val manager = ProjectManager.getInstance
    if (ApplicationManager.getApplication.isUnitTestMode) {
      val testProject = manager.asInstanceOf[ProjectManagerEx].getOpenProjects.find(!_.isDisposed).getOrElse(null)
      if (testProject != null) Array(testProject)
      else Array.empty
    } else {
      manager.getOpenProjects.filter(!_.isDisposed)
    }
  }

  def obtainProject: Project = {
    val manager = ProjectManager.getInstance
    val projects = openedNotDisposedProjects
    if (projects.length == 0) manager.getDefaultProject
    else projects(0)
  }


  def isScalaFile(file: VirtualFile): Boolean =
    try isScalaFile(file, file.contentsToByteArray)
    catch {
      case e: IOException => false
    }
  def isScalaFile(file: VirtualFile, bytes: => Array[Byte]): Boolean = decompile(file, bytes).isScala
  def decompile(file: VirtualFile, bytes: => Array[Byte]): DecompilationResult = {
    if (file.getFileType != StdFileTypes.CLASS) return DecompilationResult.empty
    if (!file.isInstanceOf[VirtualFileWithId]) return DecompilationResult.empty
    val timeStamp = file.getTimeStamp
    var data = file.getUserData(SCALA_DECOMPILER_KEY)
    var res: DecompilationResult = if (data == null) null else data.get()
    if (data == null || data.get() == null || data.get().timeStamp != timeStamp) {
      val readAttribute = SCALA_DECOMPILER_FILE_ATTRIBUTE.readAttribute(file)
      def updateAttributeAndData() {
        val writeAttribute = SCALA_DECOMPILER_FILE_ATTRIBUTE.writeAttribute(file)
        val decompilationResult = decompileInner(file, bytes)
        try {
          writeAttribute.writeBoolean(decompilationResult.isScala)
          writeAttribute.writeUTF(decompilationResult.sourceName)
          writeAttribute.writeUTF(decompilationResult.sourceText)
          writeAttribute.writeLong(decompilationResult.timeStamp)
          writeAttribute.close()
        } catch {
          case e: IOException =>
        }
        res = decompilationResult
      }
      if (readAttribute != null) {
        try {
          val isScala = readAttribute.readBoolean()
          val sourceName = readAttribute.readUTF()
          val sourceText = readAttribute.readUTF()
          val attributeTimeStamp = readAttribute.readLong()
          if (attributeTimeStamp != timeStamp) updateAttributeAndData()
          else res = DecompilationResult(isScala, sourceName, sourceText, attributeTimeStamp)
        }
        catch {
          case e: IOException => updateAttributeAndData()
        }
      } else updateAttributeAndData()
      data = new SoftReference[DecompilationResult](res)
      file.putUserData(SCALA_DECOMPILER_KEY, data)
    }
    res
  }

  private val SOURCE_FILE = "SourceFile"
  private val SCALA_SIG = "ScalaSig"
  private val SCALA_SIG_ANNOTATION = "Lscala/reflect/ScalaSignature;"
  private val BYTES_VALUE = "bytes"
  private def decompileInner(file: VirtualFile, bytes: Array[Byte]): DecompilationResult = {
    try {
      val byteCode = ByteCode(bytes)
      val isPackageObject = file.getName == "package.class"
      val classFile = ClassFileParser.parse(byteCode)
      val scalaSig = classFile.attribute(SCALA_SIG).map(_.byteCode).map(ScalaSigAttributeParsers.parse).
        getOrElse(null) match {
        // No entries in ScalaSig attribute implies that the signature is stored in the annotation
        case ScalaSig(_, _, entries) if entries.length == 0 =>
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
        case other => other
      }
      if (scalaSig == null) return DecompilationResult(false, "", "", file.getTimeStamp)
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
              stream.print("package ")
              stream.print(path)
              stream.print("\n")
            } else {
              val i = path.lastIndexOf(".")
              if (i > 0) {
                stream.print("package ")
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
        classFile.attribute(SOURCE_FILE) match {
          case Some(attr: Attribute) =>
            val SourceFileInfo(index: Int) = SourceFileAttributeParser.parse(attr.byteCode)
            val c = classFile.header.constants(index)
            val sBytes: Array[Byte] = c match {
              case s: String => s.getBytes(UTF8)
              case scala.tools.scalap.scalax.rules.scalasig.StringBytesPair(s: String, bytes: Array[Byte]) => bytes
              case _ => Array.empty
            }
            new String(sBytes, UTF8)
          case None => "-no-source-"
        }
      }

      DecompilationResult(true, sourceFileName, sourceText, file.getTimeStamp)
    } catch {
      case t: Throwable =>
//        LOG.info(s"Error during decompiling ${file.getName}: ${t.getMessage}", t)
        DecompilationResult(false, "", "", file.getTimeStamp)
    }
  }
}
