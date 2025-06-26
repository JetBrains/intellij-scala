package org.jetbrains.plugins.scala.project.sdkdetect.repository

import java.io.{BufferedInputStream, File}
import java.nio.file.{Files, Path}
import java.util.zip.ZipInputStream
import scala.util.Using

private object ZipUtils {

  // AI-generated
  def unzip(zipFile: Path, targetDir: Path): Unit = {
    Using.resource(new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) { zipIn =>
      var entry = zipIn.getNextEntry
      while (entry != null) {
        val filePath = targetDir.resolve(entry.getName)

        if (!entry.isDirectory) {
          // Create parent directories if they don't exist
          Files.createDirectories(filePath.getParent)

          // Extract file
          Using.resource(Files.newOutputStream(filePath)) { outputStream =>
            val buffer = new Array[Byte](8192)
            var bytesRead = 0
            while ( {
              bytesRead = zipIn.read(buffer);
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
