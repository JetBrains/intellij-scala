package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.incremental.scala.remote.{Commands, CompileServerArgs}

import scala.util.Try

trait CompileServerArgsParser {

  def parse(command: String, args: Seq[String]): Try[CompileServerArgs]
}

object CompileServerArgsParser
  extends CompileServerArgsParser {

  def parse(command: String, args: Seq[String]): Try[CompileServerArgs] = Try {
    command match {
      case Commands.Compile =>
        ArgumentsParser.parse(args) match {
          case Right(arguments) => CompileServerArgs.Compile(arguments)
          case Left(t) => throw t
        }
      case Commands.CompileJps =>
        args match {
          case Seq(token, projectPath, globalOptionsPath) =>
            CompileServerArgs.CompileJps(
              token = token,
              projectPath = projectPath,
              globalOptionsPath = globalOptionsPath
            )
          case wrong =>
            throw new IllegalArgumentException(s"Can't parse args for $command: $wrong")
        }
      case unknownCommand =>
        throw new IllegalArgumentException(s"Unknown command: $unknownCommand")
    }
  }
}

