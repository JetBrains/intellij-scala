package org.jetbrains.plugins.scala.config

import java.io.File
import java.util.List

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.scala.config.FileAPI._

import scala.collection.JavaConversions._

/**
 * Pavel.Fatin, 31.07.2010
 */

object CompilerPlugin {
  def fromPaths(paths: Array[String], module: Module): List[CompilerPlugin] = 
    paths.map(new CompilerPlugin(_, module)).toList
  
  def toPaths(plugins: List[CompilerPlugin]): Array[String] = plugins.map(_.path).toArray
  
  def pathTo(file: File, module: Module) = {
    val base = VfsUtilCore.virtualToIoFile(module.getProject.getBaseDir)
    val path = Option(FileUtil.getRelativePath(base, file)).getOrElse(file.getPath)
    if(path.contains("..")) file.getPath else path
  }
}

class CompilerPlugin(val path: String, module: Module) {
  private val NamePattern = """<name>\s*(.*?)\s*</name>""".r
  
  private val Base = VfsUtilCore.virtualToIoFile(module.getProject.getBaseDir)

  val file = optional(new File(path)).getOrElse(new File(Base, path))
  
  val name = {
    val r = optional(file).flatMap(readEntry(_, "scalac-plugin.xml")).flatMap { content =>
      NamePattern.findFirstMatchIn(content).map(_.group(1))
    }
    r.mkString
  }
}