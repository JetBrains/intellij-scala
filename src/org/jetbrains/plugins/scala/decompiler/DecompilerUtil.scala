package org.jetbrains.plugins.scala
package decompiler

import java.io._

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.reference.SoftReference

import scalap.Decompiler

/**
 * @author ilyas
 */
object DecompilerUtil {
  protected val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.decompiler.DecompilerUtil")

  val DECOMPILER_VERSION = 266
  private val SCALA_DECOMPILER_FILE_ATTRIBUTE = new FileAttribute("_is_scala_compiled_new_key_", DECOMPILER_VERSION, true)
  private val SCALA_DECOMPILER_KEY = new Key[SoftReference[DecompilationResult]]("Is Scala File Key")

  class DecompilationResult(val isScala: Boolean, val sourceName: String, val timeStamp: Long) {
    def sourceText: String = ""
  }
  object DecompilationResult {
    def empty: DecompilationResult = new DecompilationResult(isScala = false, sourceName = "", timeStamp = 0L)
  }

  private def openedNotDisposedProjects: Array[Project] = {
    val manager = ProjectManager.getInstance
    if (ApplicationManager.getApplication.isUnitTestMode) {
      val testProject = manager.asInstanceOf[ProjectManagerEx].getOpenProjects.find(!_.isDisposed).orNull
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

  // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
  private def attributesSupported = !ScalaLoader.isUnderUpsource

  def isScalaFile(file: VirtualFile): Boolean =
    try isScalaFile(file, file.contentsToByteArray)
    catch {
      case e: IOException => false
    }
  def isScalaFile(file: VirtualFile, bytes: => Array[Byte]): Boolean = decompile(file, bytes).isScala
  def decompile(file: VirtualFile, bytes: => Array[Byte]): DecompilationResult = {
    if (!file.isInstanceOf[VirtualFileWithId]) return DecompilationResult.empty
    val timeStamp = file.getTimeStamp
    var data = file.getUserData(SCALA_DECOMPILER_KEY)
    var res: DecompilationResult = if (data == null) null else data.get()
    if (res == null || res.timeStamp != timeStamp) {
      val readAttribute = if (attributesSupported) SCALA_DECOMPILER_FILE_ATTRIBUTE.readAttribute(file) else null
      def updateAttributeAndData() {
        val decompilationResult = decompileInner(file, bytes)
        if (attributesSupported) {
          val writeAttribute = SCALA_DECOMPILER_FILE_ATTRIBUTE.writeAttribute(file)
          try {
            writeAttribute.writeBoolean(decompilationResult.isScala)
            writeAttribute.writeUTF(decompilationResult.sourceName)
            writeAttribute.writeLong(decompilationResult.timeStamp)
            writeAttribute.close()
          } catch {
            case e: IOException =>
          }
        }
        res = decompilationResult
      }
      if (readAttribute != null) {
        try {
          val isScala = readAttribute.readBoolean()
          val sourceName = readAttribute.readUTF()
          val attributeTimeStamp = readAttribute.readLong()
          if (attributeTimeStamp != timeStamp) updateAttributeAndData()
          else res = new DecompilationResult(isScala, sourceName, attributeTimeStamp) {
            override lazy val sourceText: String = {
              decompileInner(file, bytes).sourceText
            }
          }
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

  private def decompileInner(file: VirtualFile, bytes: Array[Byte]): DecompilationResult = {
    try {
      Decompiler.decompile(file.getName, bytes) match {
        case Some((sourceFileName, decompiledSourceText)) =>
          new DecompilationResult(isScala = true, sourceFileName, file.getTimeStamp) {
            override def sourceText: String = decompiledSourceText
          }
        case _ => new DecompilationResult(isScala = false, "", file.getTimeStamp)
      }
    } catch {
      case m: MatchError =>
        LOG.warn(s"Error during decompiling $file: ${m.getMessage()}. Stacktrace is suppressed.")
        new DecompilationResult(isScala = false, "", file.getTimeStamp)
      case t: Throwable =>
        LOG.warn(s"Error during decompiling $file: ${t.getMessage}. Stacktrace is suppressed.")
        new DecompilationResult(isScala = false, "", file.getTimeStamp)
    }
  }
}
