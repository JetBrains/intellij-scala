//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.sbt.project

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelNioBridgeServiceKt
import org.jetbrains.sbt.project.structure.data.{InterpretablePath, PathInterpreter}

import java.nio.file.Path

extension (path: InterpretablePath)
  def toPath(using EelDescriptor): Path =
    val interpreter = summon[PathInterpreter[Path]]
    interpreter.interpret(path)

given eelPathInterpreter: (descriptor: EelDescriptor) => PathInterpreter[Path] = new PathInterpreter[Path]:
  override def interpret(path: InterpretablePath): Path =
    val eelPath = EelPath.parse(path.unsafePathString, descriptor)
    EelNioBridgeServiceKt.asNioPath(eelPath)
