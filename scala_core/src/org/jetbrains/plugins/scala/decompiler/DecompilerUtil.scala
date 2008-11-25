package org.jetbrains.plugins.scala.decompiler

import _root_.scala.runtime.RichBoolean
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

  private val decompiledTextAttribute = new FileAttribute("_file_decompiled_text_", 3)
  private val isScalaCompiledAttribute = new FileAttribute("_is_scala_compiled_", 3)

  def isScalaFile(file: VirtualFile) : Boolean = isScalaFile(file, file.contentsToByteArray)
  
  def isScalaFile(file: VirtualFile, bytes : => Array[Byte]) : Boolean = {
    if (file.getFileType != StdFileTypes.CLASS) return false
    val read = isScalaCompiledAttribute.readAttribute(file)
    if (read != null) try {read.readBoolean} finally {read.close} else {
      val byteCode = ByteCode(bytes)
      val classFile = ClassFileParser.parse(byteCode)
      val isScala = classFile.attribute("ScalaSig") match {case Some(_) => true; case None => false}
      val write = isScalaCompiledAttribute.writeAttribute(file)
      write.writeBoolean(isScala)
      write.close
      isScala
    }
  }

  def obtainProject: Project = {
    val manager = ProjectManager.getInstance
    if (ApplicationManager.getApplication.isUnitTestMode) {
      manager.asInstanceOf[ProjectManagerEx].getCurrentTestProject
    } else {
      val projects = manager.getOpenProjects();
      if (projects.length == 0) manager.getDefaultProject else projects(0)
    }
  }

  def decompile(bytes: Array[Byte], file: VirtualFile) = {
    val byteCode = ByteCode(bytes)
    val ba = decompiledTextAttribute.readAttributeBytes(file)
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
          decompiledTextAttribute.writeAttributeBytes(file, bs, 0, bs.length)
          bs
        }
      }
    }
    new String(bts, CharsetToolkit.UTF8)
  }
}