package org.jetbrains.plugins.scala.compiler.data.serialization

import java.io.File

import com.intellij.openapi.util.io.FileUtil

import scala.util.Try

/*private[data]*/ object SerializationUtils {

  final val Delimiter = "\n"

  // serializing
  def fileToPath(file: File): String = FileUtil.toCanonicalPath(file.getPath)
  def filesToPaths(files: Iterable[File]): String = sequenceToString(files.map(fileToPath))
  def optionToString(s: Option[String]): String = s.getOrElse("")
  def sequenceToString(strings: Iterable[String]): String = strings.mkString(Delimiter)

  // deserializing
  // probably better separate parsing from validation (nullability, file existence, etc...), but nit critical now
  def notNull(value: String, argName: String): Either[String, String] =
    Option(value).toRight(s"Argument '$argName' is null")

  def boolean(value: String, argName: String): Either[String, Boolean] = {
    val notNullValue = Option(value).toRight(s"Argument '$argName' is null")
    notNullValue.flatMap(s => Try(s.toBoolean).toEither.left.map(_ => s"Invalid boolean value for argument '$argName': $value"))
  }

  def pathToFileValidated(path: String, argName: String): Either[String, File] =
    for {
      p <- Option(path).toRight(s"File $argName is null")
      f = new File(p)
      _ <- Right(f).filterOrElse(_.exists, s"File '$argName' with path ${f.getAbsolutePath} doesn't exist")
    } yield f

  def pathToFile(path: String, argName: String): Option[File] =
    pathToFileValidated(path, argName).toOption
}
