package org.jetbrains.plugins.scala.compiler.data.serialization

import org.jetbrains.plugins.scala.compiler.data.serialization.ArgListSerializer._
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs

/** TODO: cover with property-based tests */
object WorksheetArgsSerializer extends ArgListSerializer[WorksheetArgs] {

  private val ReplMode  = "repl"
  private val PlainMode = "plain"

  override def serialize(value: WorksheetArgs): ArgList = value match {
    case plain: WorksheetArgs.RunPlain => PlainMode +: WorksheetArgsPlainSerializer.serialize(plain)
    case repl: WorksheetArgs.RunRepl   => ReplMode +: WorksheetArgsReplSerializer.serialize(repl)
  }

  override def deserialize(args: ArgList): Either[DeserializationError, WorksheetArgs] =
    args match {
      case Seq()                      => error("Args are empty")
      case Seq(PlainMode, other@_*)   => WorksheetArgsPlainSerializer.deserialize(other)
      case Seq(ReplMode, other@_*)    => WorksheetArgsReplSerializer.deserialize(other)
      case Seq(unknown, _*)           => error(s"Unknown worksheet run mode: $unknown")
    }
}

object WorksheetArgsPlainSerializer extends ArgListSerializer[WorksheetArgs.RunPlain] {

  import SerializationUtils._

  override def serialize(value: WorksheetArgs.RunPlain): ArgList = Seq(
    value.worksheetClassName,
    fileToPath(value.pathToRunnersJar),
    fileToPath(value.worksheetTempFile),
    value.originalFileName,
    filesToPaths(value.outputDirs)
  )

  override def deserialize(args: ArgList): Either[DeserializationError, WorksheetArgs.RunPlain] =
    (for {
      worksheetClassName <- Right(args.head)
      pathToRunners      <- pathToFileValidated(args(1), "pathToRunners")
      worksheetTempFile  <- pathToFileValidated(args(2), "worksheetTempFile")
      originalFileName   <- notNull(args(3), "originalFileName")
      outputDirs         = args.drop(4).flatMap(pathToFile(_, "outputDirs"))
    } yield WorksheetArgs.RunPlain(
      worksheetClassName,
      pathToRunners,
      worksheetTempFile,
      originalFileName,
      outputDirs
    )).left.map(Seq(_))
}

object WorksheetArgsReplSerializer extends ArgListSerializer[WorksheetArgs.RunRepl] {

  import SerializationUtils._

  override def serialize(value: WorksheetArgs.RunRepl): ArgList = Seq(
    value.sessionId,
    value.codeChunk,
    value.dropCachedReplInstance.toString,
    value.continueOnChunkError.toString,
    SerializationUtils.filesToPaths(value.outputDirs)
  )

  override def deserialize(args: ArgList): Either[DeserializationError, WorksheetArgs.RunRepl] =
    (for {
      sessionId            <- notNull(args.head, "repl session id")
      codeChunk            <- notNull(args(1), "codeChunk")
      dropReplInstance     <- boolean(args(2), "dropCachedReplInstance")
      continueOnChunkError <- boolean(args(3), "continueOnChunkError")
      outputDirs           = args.drop(4).flatMap(SerializationUtils.pathToFile(_, "outputDirs"))
    } yield WorksheetArgs.RunRepl(
      sessionId,
      codeChunk,
      dropReplInstance,
      continueOnChunkError,
      outputDirs
    )).left.map(Seq(_))
}