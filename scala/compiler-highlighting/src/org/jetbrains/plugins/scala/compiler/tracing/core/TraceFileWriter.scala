package org.jetbrains.plugins.scala.compiler.tracing.core

import com.intellij.openapi.diagnostic.Logger

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.control.NonFatal

/**
 * Appends pre-rendered trace entries to a file while maintaining the [[TraceFormat]] envelope.
 *
 * Instead of appending at the very end of the file, every batch is inserted just before the format's
 * `epilogue`: the writer seeks to `size - epilogue`, overwrites the old epilogue with
 * `separator`-joined entries followed by the epilogue again. The file therefore stays well-formed
 * (e.g. a valid JSON array) after each [[append]] call.
 *
 * I/O goes through a nio [[SeekableByteChannel]] (via [[Files.newByteChannel]]) rather than a
 * `RandomAccessFile`, so it is dispatched to the filesystem provider that owns `path`.
 *
 * This class is intentionally not thread-safe: it keeps the "is the array empty" state in a plain
 * field.
 *
 * @param path   The file to write to; created (together with missing parent directories) on first use.
 * @param format How entries are framed in the file.
 */
private[tracing] final class TraceFileWriter(path: Path, format: TraceFormat) {

  private val Log: Logger = Logger.getInstance(classOf[TraceFileWriter])
  private val epilogueByteLength = format.epilogue.getBytes(UTF_8).length
  /** Whether the file has been created and seeded with the prologue/epilogue during this session. */
  private var initialized = false
  /** Whether no entry has been written yet, i.e. the next entry must not be preceded by a separator. */
  private var empty = true
  /**
   * Set after an I/O failure (e.g. a filesystem provider that can't open the path); disables further
   * writes so a broken destination can't throw into the tracing/compile path or spam the log every flush.
   */
  private var disabled = false

  /**
   * Inserts `entries` into the file, before the trailing epilogue. The first call truncates any
   * pre-existing file and writes a fresh, empty envelope. Does nothing for empty `entries`, or once
   * writing has been disabled by an earlier failure.
   */
  def append(entries: Seq[String]): Unit = {
    if (entries.isEmpty || disabled) return

    try {
      if (!initialized) Option(path.getParent).foreach(Files.createDirectories(_))

      val channel = Files.newByteChannel(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
      try {
        if (!initialized) {
          channel.truncate(0)
          writeFully(channel, format.prologue)
          writeFully(channel, format.epilogue)
          initialized = true
          empty = true
        }

        // Position the write head right before the epilogue so the new entries land inside the envelope.
        channel.position(channel.size() - epilogueByteLength)

        val builder = new StringBuilder
        entries.foreach { entry =>
          if (!empty) builder.append(format.separator)
          builder.append(entry)
          empty = false
        }
        builder.append(format.epilogue)

        // The replacement is strictly longer than the epilogue it overwrites, so the file grows and no
        // truncation is needed.
        writeFully(channel, builder.toString)
        Log.info(s"[tracing] wrote ${entries.size} entries to $path (size now ${channel.size()} bytes)")
      } finally channel.close()
    } catch {
      case NonFatal(t) =>
        disabled = true
        Log.warn(s"[tracing] disabling trace file writing for $path after an I/O failure", t)
    }
  }

  private def writeFully(channel: SeekableByteChannel, s: String): Unit = {
    val buffer = ByteBuffer.wrap(s.getBytes(UTF_8))
    while (buffer.hasRemaining) channel.write(buffer)
  }
}
