package org.jetbrains.plugins.scala.decompiler

import annotations.Nullable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.vfs.VirtualFile
import java.io.{ByteArrayOutputStream, FileNotFoundException}
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

  def decompile(bytes: Array[Byte], file: VirtualFile) = {
    val byteCode = ByteCode(bytes)
    try {
      val classFile = ClassFileParser.parse(byteCode)
      classFile.attribute("ScalaSig").map(_.byteCode).map(ScalaSigAttributeParsers.parse) match {
        case Some(scalaSig) => {
          val stream = new ByteArrayOutputStream
          Console.withOut(stream){
            val owner = ((scalaSig.topLevelClass, scalaSig.topLevelObject) match {
              case (Some(c), _) => c.symbolInfo.owner
              case (_, Some(o)) => o.symbolInfo.owner
              case _ => ""
            }).toString
            if (owner.length > 0) {print("package "); print(owner)}
            {println; println}
            // Print classes
            for (c <- scalaSig.topLevelClass) ScalaSigPrinter.printSymbol(c)
            println
            for (o <- scalaSig.topLevelObject) ScalaSigPrinter.printSymbol(o)
          }
          stream.toString
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