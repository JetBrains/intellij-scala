package org.jetbrains.plugins.scala.compiler.data.serialization

import org.jetbrains.jps.incremental.scala.remote.PathTranslator
import org.jetbrains.plugins.scala.compiler.data.serialization.ArgListSerializer._
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs

/** TODO: cover with property-based tests */
object WorksheetArgsSerializer extends ArgListSerializer[WorksheetArgs] {

  private final val ReplMode  = "repl"
  private final val PlainMode = "plain"

  override def serialize(value: WorksheetArgs, translator: PathTranslator): ArgList = value match {
    case plain: WorksheetArgs.RunPlain => PlainMode +: WorksheetArgsPlainSerializer.serialize(plain, translator)
    case repl: WorksheetArgs.RunRepl   => ReplMode +: WorksheetArgsReplSerializer.serialize(repl, translator)
  }

  override def deserialize(args: ArgList): Either[DeserializationError, WorksheetArgs] =
    args match {
      case Seq()                  => error("Args are empty")
      case Seq(PlainMode, other*) => WorksheetArgsPlainSerializer.deserialize(other)
      case Seq(ReplMode, other*)  => WorksheetArgsReplSerializer.deserialize(other)
      case Seq(unknown, _*)       => error(s"Unknown worksheet run mode: $unknown")
    }
}

object WorksheetArgsPlainSerializer extends ArgListSerializer[WorksheetArgs.RunPlain] {

  import SerializationUtils.{notNull, pathToString, pathsToString, stringToPath, stringToPathValidated}

  override def serialize(value: WorksheetArgs.RunPlain, translator: PathTranslator): ArgList =
    Seq(
      value.worksheetClassName,
      pathToString(value.pathToRunnersJar, translator),
      pathToString(value.worksheetTempFile, translator),
      value.originalFileName,
      pathsToString(value.outputDirs, translator)
    )

  override def deserialize(args: ArgList): Either[DeserializationError, WorksheetArgs.RunPlain] =
    (for {
      worksheetClassName <- Right(args.head)
      pathToRunners      <- stringToPathValidated(args(1), "pathToRunners")
      worksheetTempFile  <- stringToPathValidated(args(2), "worksheetTempFile")
      originalFileName   <- notNull(args(3), "originalFileName")
      outputDirs         = args.drop(4).flatMap(stringToPath(_, "outputDirs"))
    } yield WorksheetArgs.RunPlain(
      worksheetClassName,
      pathToRunners,
      worksheetTempFile,
      originalFileName,
      outputDirs
    )).left.map(Seq(_))
}

object WorksheetArgsReplSerializer extends ArgListSerializer[WorksheetArgs.RunRepl] {

  import SerializationUtils.{boolean, notNull, pathsToString}

  override def serialize(value: WorksheetArgs.RunRepl, translator: PathTranslator): ArgList =
    Seq(
      value.sessionId,
      value.codeChunk,
      value.dropCachedReplInstance.toString,
      value.continueOnChunkError.toString,
      pathsToString(value.outputDirs, translator)
    )

  override def deserialize(args: ArgList): Either[DeserializationError, WorksheetArgs.RunRepl] =
    (for {
      sessionId            <- notNull(args.head, "repl session id")
      codeChunk            <- notNull(args(1), "codeChunk")
      dropReplInstance     <- boolean(args(2), "dropCachedReplInstance")
      continueOnChunkError <- boolean(args(3), "continueOnChunkError")
      outputDirs           = args.drop(4).flatMap(SerializationUtils.stringToPath(_, "outputDirs"))
    } yield WorksheetArgs.RunRepl(
      sessionId,
      codeChunk,
      dropReplInstance,
      continueOnChunkError,
      outputDirs
    )).left.map(Seq(_))
}
