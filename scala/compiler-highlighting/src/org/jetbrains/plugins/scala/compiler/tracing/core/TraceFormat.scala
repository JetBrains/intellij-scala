package org.jetbrains.plugins.scala.compiler.tracing.core
/**
 * Describes how a sequence of already-rendered trace entries is framed inside the output file.
 *
 * A file written with a format always looks like `prologue` + entries joined by `separator` +
 * `epilogue`. [[TraceFileWriter]] keeps the `epilogue` at the tail and inserts new entries right
 * before it, so the file is well-formed after every write.
 *
 * @param prologue  Written once when the file is first created (e.g. the opening `[` of a JSON array).
 * @param epilogue  Kept at the end of the file at all times (e.g. the closing `]`). New entries are
 *                  inserted immediately before it.
 * @param separator Placed between consecutive entries (e.g. `,` for a JSON array, a newline for text).
 */
final case class TraceFormat(prologue: String, epilogue: String, separator: String)

object TraceFormat {

  /**
   * A JSON array: entries are objects separated by commas, wrapped in `[` … `]`. Produces a file that
   * is a valid JSON array after every flush, suitable for the Chrome / Perfetto trace viewer.
   */
  val JsonArray: TraceFormat = TraceFormat(prologue = "[\n", epilogue = "\n]", separator = ",\n")

  /** Plain text: each entry on its own line, no wrapping. */
  val PlainText: TraceFormat = TraceFormat(prologue = "", epilogue = "", separator = "\n")

  val JaegerUI: TraceFormat = TraceFormat(
    prologue = "{\"data\": [\n",
    epilogue = "\n]}",
    separator = ",\n"
  )
}
