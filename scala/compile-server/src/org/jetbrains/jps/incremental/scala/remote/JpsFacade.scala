package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.jps.incremental.scala.Client

import java.nio.file.Path

trait JpsFacade:
  def compileJpsLogic(command: CompileServerCommand.CompileJps, client: Client, jpsBuildSystemDir: Path): Unit
