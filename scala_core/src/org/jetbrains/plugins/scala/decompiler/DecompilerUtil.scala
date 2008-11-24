package org.jetbrains.plugins.scala.decompiler

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

  def isScalaFile(file: VirtualFile): Boolean = {
    if (file.getFileType != StdFileTypes.CLASS) return false
    val bytes = file.contentsToByteArray()
    isScalaFile(bytes)
  }

  def isScalaFile(bytes: Array[Byte]) = {
    val bc = ByteCode(bytes)
    val classFile = ClassFileParser.parse(bc)
    classFile.attribute("ScalaSig") match {
      case None => false
      case _ => true
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

  val _error_msg = "//ScalaSig parsing error"

  private val myFileSourceTextAttr = new FileAttribute("_file_source_text_", 1)

  def decompile(bytes: Array[Byte], file: VirtualFile) = {
    val byteCode = ByteCode(bytes)
    val ba = myFileSourceTextAttr.readAttributeBytes(file)
    if (ba != null) new String(ba, CharsetToolkit.UTF8_CHARSET)
    else try {
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
          val text = baos.toString
          val bs = text.getBytes(CharsetToolkit.UTF8_CHARSET)
          myFileSourceTextAttr.writeAttributeBytes(file, bs, 0, bs.length)
          text
        }
      }
    }
    catch {
      case pe: ScalaSigParserError => {
        LOG.error(pe)
        _error_msg
      }
    }
  }
}