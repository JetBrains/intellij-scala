package org.jetbrains.sbt.project

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.file.Path

object EelPathKotlinUtils {

  /**
   * Creates a temporary file in the specified [parentDirectory] using the EEL API.
   * It's created based on [com.intellij.platform.eel.provider.utils.EelPathUtils.createTemporaryFile].
   *
   * A custom implementation was necessary. Any attempt to create a temporary file on the local machine
   * and transfer it to the remote in a specific parent directory caused the file not to be deleted on exit
   * within eel or to resolve to an incorrect path.
   *
   * This should be removed when https://youtrack.jetbrains.com/issue/IJPL-239190 is implemented.
   */
  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun createTemporaryFile(prefix: String, suffix: String, parentDirectory: Path, eelDescriptor: EelDescriptor): Path {
    return runBlockingMaybeCancellable {
      val eel = eelDescriptor.toEelApi()
      val file = eel.fs.createTemporaryFile()
        .suffix(suffix)
        .parentDirectory(parentDirectory.asEelPath())
        .prefix(prefix)
        .deleteOnExit(true)
        .getOrThrowFileSystemException()
      file.asNioPath()
    }
  }
}