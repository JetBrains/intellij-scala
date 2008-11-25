package org.jetbrains.plugins.scala.decompiler

import _root_.scala.runtime.RichBoolean
import annotations.Nullable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{CharsetToolkit, VirtualFile}
import java.io.{PrintStream, ByteArrayOutputStream, FileNotFoundException}
import java.nio.ByteBuffer
import scalax.rules.scalasig.{ClassFileParser, ScalaSigAttributeParsers, ScalaSigPrinter, ByteCode}
import scalax.rules.ScalaSigParserError

/**
 * @author ilyas
 */

object DecompilerUtil {
  protected val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentListImpl");

  private val myFileSourceTextAttr = new FileAttribute("_file_source_text_", 3)
  private val isScalaFileAttr = new FileAttribute("_is_scala_file_", 3)

  def isScalaFile(file: VirtualFile): Boolean = {
    if (file.getFileType != StdFileTypes.CLASS) return false
    val bytes = file.contentsToByteArray()
    isScalaFile(file, bytes)
  }

  def isScalaFile(file: VirtualFile, bytes: Array[Byte]) = {
    val attr = isScalaFileAttr.readAttributeBytes(file)
    if (attr != null) {
      attr(0) == 1
    } else {
      val bc = ByteCode(bytes)
      val classFile = ClassFileParser.parse(bc)
      val res = classFile.attribute("ScalaSig") match {
        case None => false
        case _ => true
      }
      val bs: Array[Byte] = Array(if (res) 1 else 0)
      isScalaFileAttr.writeAttributeBytes(file, bs, 0, bs.length)
      res
    }
  }

  @Nullable
  def obtainProject: Project = {
    var project: Project = null
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      project = ProjectManager.getInstance().asInstanceOf[ProjectManagerEx].getCurrentTestProject
    } else {
      val projects = ProjectManager.getInstance().getOpenProjects();
      if (projects.length == 0) {
        return null
      }
      project = projects(0)
    }
    project
  }

  def decompile(bytes: Array[Byte], file: VirtualFile) = {
    val byteCode = ByteCode(bytes)
    val ba = myFileSourceTextAttr.readAttributeBytes(file)
    val bts = if (ba != null) ba else {
      val classFile = ClassFileParser.parse(byteCode)
      classFile.attribute("ScalaSig").map(_.byteCode).map(ScalaSigAttributeParsers.parse) match {
        case Some(scalaSig) => {
          val baos = new ByteArrayOutputStream
          val stream = new PrintStream(baos)
          val syms = scalaSig.topLevelClasses ::: scalaSig.topLevelObjects
          syms.first.parent match {
            case Some(p) if (p.name != "<empty>") => {
              stream.print("package ");
              stream.print(p.path);
              stream.println
            }
            case _ =>
          }
          // Print classes
          val printer = new ScalaSigPrinter(stream)
          for (c <- syms) {
            println
            printer.printSymbol(c)
          }
          val bs = baos.toByteArray
          myFileSourceTextAttr.writeAttributeBytes(file, bs, 0, bs.length)
          bs
        }
      }
    }
    new String(bts, CharsetToolkit.UTF8)
  }
}