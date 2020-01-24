package org.jetbrains.jps.incremental.scala.local

import java.io.File

import com.intellij.openapi.util.io.FileUtil

object ClassFileUtils {

  /**
   * Returns corresponding .tasty-file for specified .class-file.
   * If specified file is not a .class-file than method returns None.
   *
   * @return .tasty-file if it exists
   */
  def correspondingTastyFile(classFile: File): Option[File] = {
    val canonicalPath = FileUtil.toCanonicalPath(classFile.getPath)
    if (canonicalPath.endsWith(".class")) {
      val tastyCanonicalPath = canonicalPath.split('.').init.mkString("", ".", ".tasty")
      val tastyFile = new File(tastyCanonicalPath)
      Some(tastyFile).filter(_.exists)
    } else {
      None
    }
  }
}
