package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.plugins.scala.compiler.data.Arguments

import scala.concurrent.duration.FiniteDuration

sealed trait CompileServerCommand {

  def token: String

  def asArgs: Seq[String]

  def id: String

  def isCompileCommand: Boolean

  final def asArgsWithoutToken: Seq[String] =
    asArgs.tail // token must be the first argument
}

object CompileServerCommand {

  case class Compile(arguments: Arguments)
    extends CompileServerCommand {

    override def token: String = arguments.token

    override def id: String = CommandIds.Compile

    override def asArgs: Seq[String] = arguments.asStrings

    override def isCompileCommand: Boolean = true
  }

  case class CompileJps(token: String,
                        projectPath: String,
                        globalOptionsPath: String,
                        dataStorageRootPath: String,
                        testScopeOnly: Boolean,
                        forceCompileModule: Option[String])
    extends CompileServerCommand {

    override def id: String = CommandIds.CompileJps

    override def asArgs: Seq[String] = Seq(
      token,
      projectPath,
      globalOptionsPath,
      dataStorageRootPath,
      testScopeOnly.toString,
      forceCompileModule.getOrElse(""),
    )

    override def isCompileCommand: Boolean = true
  }

  case class StartMetering(token: String, meteringInterval: FiniteDuration)
    extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq(token, meteringInterval.toSeconds.toString)

    override def id: String = CommandIds.StartMetering

    override def isCompileCommand: Boolean = false
  }

  case class EndMetering(token: String)
    extends CompileServerCommand {

    override def asArgs: Seq[String] = Seq(token)

    override def id: String = CommandIds.EndMetering

    override def isCompileCommand: Boolean = false
  }
}

