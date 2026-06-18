package org.jetbrains.sbt.shell.process.utils

/**
 * An id to identify this boot of sbt as being launched from IntelliJ IDEA
 *
 * It's needed so injected plugins are never loaded outside this sbt shell boot.
 *
 * This avoids failing to reload when multiple sbt instances are booted from IDEA (SCL-12009)
 */
opaque type SbtShellRunId = String

object SbtShellRunId {
  def apply(id: String): SbtShellRunId = id

  extension (id: SbtShellRunId)
    def value: String = id
}
