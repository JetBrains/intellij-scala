package org.jetbrains.plugins.scala.project.utils

import java.io.BufferedInputStream
import java.nio.file.{Files, Path}
import java.util.zip.ZipInputStream
import scala.util.Using

private object ZipUtils {

  // AI-generated
  def unzip(zipFile: Path, targetDir: Path, customRootDir: Option[String] = None): Unit = {
    Using.resource(new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) { zipIn =>
      var entry = zipIn.getNextEntry
      while (entry != null) {
        val entryPath = customRootDir match {
          case Some(rootDir) =>
            // Replace the first path component with custom root directory
            val parts = entry.getName.split("/", 2)
            if (parts.length == 2) s"$rootDir/${parts(1)}" else rootDir
          case None => entry.getName
        }
        val filePath = targetDir.resolve(entryPath)

        if (!entry.isDirectory) {
          // Create parent directories if they don't exist
          Files.createDirectories(filePath.getParent)

          // Extract file
          Using.resource(Files.newOutputStream(filePath)) { outputStream =>
            val buffer = new Array[Byte](8192)
            var bytesRead = 0
            while ( {
              bytesRead = zipIn.read(buffer)
              bytesRead != -1
            }) {
              outputStream.write(buffer, 0, bytesRead)
            }
          }
        } else {
          // Create directory
          Files.createDirectories(filePath)
        }

        zipIn.closeEntry()
        entry = zipIn.getNextEntry
      }
    }
  }
}
