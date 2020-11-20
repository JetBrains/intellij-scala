package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.plugins.scala.compiler.data.Arguments

import scala.concurrent.duration.FiniteDuration

sealed trait CompileServerCommand {
  def asArgs: Seq[String]

  def id: String

  def isCompileCommand: Boolean
}

object CompileServerCommand {

  case class Compile(arguments: Arguments)
    extends CompileServerCommand {

    override def id: String = CommandIds.Compile

    override def asArgs: Seq[String] = arguments.asStrings

    override def isCompileCommand: Boolean = true
  }

  case class CompileJps(projectPath: String,
                        globalOptionsPath: String,
                        dataStorageRootPath: String)
    extends CompileServerCommand {

    override def id: String = CommandIds.CompileJps

    override def asArgs: Seq[String] = Seq(
      projectPath,
      globalOptionsPath,
      dataStorageRootPath
    )

    override def isCompileCommand: Boolean = true
  }

  case class GetMetrics()
    extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq.empty

    override def id: String = CommandIds.GetMetrics

    override def isCompileCommand: Boolean = false
  }

  // TODO replace with GetMetrics
  case class StartMetering(meteringInterval: FiniteDuration)
    extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq(meteringInterval.toSeconds.toString)

    override def id: String = CommandIds.StartMetering

    override def isCompileCommand: Boolean = false
  }

  // TODO replace with GetMetrics
  case class EndMetering()
    extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq()

    override def id: String = CommandIds.EndMetering

    override def isCompileCommand: Boolean = false
  }
}

