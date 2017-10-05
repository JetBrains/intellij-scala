package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File
import java.util

import com.intellij.openapi.util.io.FileUtil

/**
 * User: Dmitry Naydanov
 * Date: 2/7/14
 */
object WorksheetBoundCompilationInfo {
  private val cache = new util.HashMap[String, (Int, File, File)]()
  
  def updateOrCreate(filePath: String, fileName: String): (Int, File, File) = {
    synchronized {
      val result = cache get filePath
      if (result != null) {
        val (it, src, out) = result
        cache.put(filePath, (it + 1, src, out))
        result
      } else {
        val src = FileUtil.createTempFile(fileName, null, true)
        val out = FileUtil.createTempDirectory(fileName, null, true)

        cache.put(filePath, (1, src, out))
        (0, src, out)
      }
    }
  }
}
