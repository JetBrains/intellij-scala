package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.incremental.scala.remote.{CommandIds, CompileServerCommand}

import scala.concurrent.duration.DurationLong
import scala.util.Try

trait CompileServerCommandParser {

  def parse(command: String, args: Seq[String]): Try[CompileServerCommand]
}

object CompileServerCommandParser
  extends CompileServerCommandParser {

  def parse(commandId: String, args: Seq[String]): Try[CompileServerCommand] = Try {
    commandId match {
      case CommandIds.Compile =>
        ArgumentsParser.parse(args) match {
          case Right(arguments) => CompileServerCommand.Compile(arguments)
          case Left(t) => throw t
        }
      case CommandIds.CompileJps =>
        args match {
          case Seq(projectPath, globalOptionsPath, dataStorageRootPath, other@_*) =>
            val (externalProjectConfig, moduleNames) = other match {
              case h +: t if h.startsWith(CompileServerCommand.CompileJps.ExternalProjectConfigTag) =>
                (Some(h.stripPrefix(CompileServerCommand.CompileJps.ExternalProjectConfigTag)), t)
              case moduleNames => (None, moduleNames)
            }

            CompileServerCommand.CompileJps(
              projectPath = projectPath,
              globalOptionsPath = globalOptionsPath,
              dataStorageRootPath = dataStorageRootPath,
              externalProjectConfig = externalProjectConfig,
              moduleNames = moduleNames
            )
          case _ =>
            throwIllegalArgs(commandId, args)
        }
      case CommandIds.GetMetrics =>
        args match {
          case Seq() =>
            CompileServerCommand.GetMetrics()
          case _ =>
            throwIllegalArgs(commandId, args)
        }
      case CommandIds.StartMetering =>
        args match {
          case Seq(meteringInterval) =>
            CompileServerCommand.StartMetering(meteringInterval.toLong.seconds)
          case _ =>
            throwIllegalArgs(commandId, args)
        }
      case CommandIds.EndMetering =>
        CompileServerCommand.EndMetering()
      case unknownCommand =>
        throw new IllegalArgumentException(s"Unknown commandId: $unknownCommand")
    }
  }

  private def throwIllegalArgs(commandId: String, args: Seq[String]): Nothing =
    throw new IllegalArgumentException(s"Can't parse args for $commandId: ${args.toList}")
}

