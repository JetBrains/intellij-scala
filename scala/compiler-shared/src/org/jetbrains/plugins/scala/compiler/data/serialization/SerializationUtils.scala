package org.jetbrains.plugins.scala.compiler.data.serialization

import org.jetbrains.jps.incremental.scala.remote.PathTranslator

import java.nio.file.{Files, Path, Paths}
import scala.util.Try

/*private[data]*/ object SerializationUtils {

  final val Delimiter = "\n"
  final val EmptyArgumentStub = "#STUB#"

  // serializing
  def pathToString(path: Path, translator: PathTranslator): String = translator.translate(path)

  def pathsToString(paths: Iterable[Path], translator: PathTranslator): String = sequenceToString(paths.map(pathToString(_, translator)))

  def optionToString(s: Option[String]): String = s.getOrElse("")

  def sequenceToString(strings: Iterable[String]): String =
    if (strings.isEmpty)
      Delimiter
    else
      strings.mkString(Delimiter)

  def stringToSequence(string: String): Seq[String] =
    if (string == Delimiter)
      Seq.empty
    else
      string.split(Delimiter).toSeq

  // deserializing
  // probably better separate parsing from validation (nullability, file existence, etc...), but nit critical now
  def notNull(value: String, argName: String): Either[String, String] =
    Option(value).toRight(s"Argument '$argName' is null")

  def boolean(value: String, argName: String): Either[String, Boolean] = {
    val notNullValue = Option(value).toRight(s"Argument '$argName' is null")
    notNullValue.flatMap(s => Try(s.toBoolean).toEither.left.map(_ => s"Invalid boolean value for argument '$argName': $value"))
  }

  def stringToPathValidated(path: String, argName: String): Either[String, Path] =
    for {
      p <- Option(path).toRight(s"File $argName is null")
      f = Paths.get(p)
      _ <- Right(f).filterOrElse(Files.exists(_), s"File '$argName' with path ${f.toAbsolutePath.normalize()} doesn't exist")
    } yield f

  def stringToPath(path: String, argName: String): Option[Path] =
    stringToPathValidated(path, argName).toOption
}
