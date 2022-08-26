package org.jetbrains.plugins.scala.projectHighlighting.reporter

final case class FileErrorDescriptor(fileName: String, error: ErrorDescriptor) {
  def summaryString: String =
    s"$fileName${error.range.toString} - ${error.message}"
}
