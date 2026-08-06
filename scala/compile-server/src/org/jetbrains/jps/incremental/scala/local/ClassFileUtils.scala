package org.jetbrains.jps.incremental.scala.local

import java.nio.file.{Files, Path, Paths}

object ClassFileUtils {

  /**
   * Returns corresponding .tasty-file for specified .class-file.
   * If specified file is not a .class-file than method returns None.
   *
   * @return .tasty-file if it exists
   */
  def correspondingTastyFile(classFile: Path): Option[Path] = {
    val canonicalPath = classFile.toAbsolutePath.normalize().toString
    if (canonicalPath.endsWith(".class")) {
      val tastyCanonicalPath = canonicalPath.split('.').init.mkString("", ".", ".tasty")
      val tastyFile = Paths.get(tastyCanonicalPath)
      Some(tastyFile).filter(Files.exists(_))
    } else {
      None
    }
  }
}
