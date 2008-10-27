package org.jetbrains.plugins.scala.decompiler


import annotations.Nullable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.vfs.VirtualFile
import java.io.{ByteArrayOutputStream, FileNotFoundException}
import scalax.rules.scalasig.{ClassFileParser, ScalaSigAttributeParsers, ScalaSigPrinter, ByteCode}

/**
 * @author ilyas
 */

object DecompilerUtil {

  def isScalaFile(file: VirtualFile): Boolean = {
    if (file.getFileType != StdFileTypes.CLASS) return false
    try {
      //todo remove scalap reference
      ScalaDecompiler.isScalaFile(file.contentsToByteArray())
    }
    catch {
      case f: FileNotFoundException => false
    }
  }


  @Nullable
  def obtainProject : Project = {
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

  def decompile(bytes : Array[Byte]) = {
    val byteCode = ByteCode(bytes)
    val classFile = ClassFileParser.parse(byteCode)
    classFile.attribute("ScalaSig").map(_.byteCode).map(ScalaSigAttributeParsers.parse) match {
      case Some(scalaSig) => {
        val stream = new ByteArrayOutputStream
        Console.withOut(stream) {
          for (c <- scalaSig.topLevelClass) ScalaSigPrinter.printSymbol(c)
          println
          for (o <- scalaSig.topLevelObject) ScalaSigPrinter.printSymbol(o)
        }
        stream.toString
      }
    }
  }
}