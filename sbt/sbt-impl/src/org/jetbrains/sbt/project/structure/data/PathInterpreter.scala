package org.jetbrains.sbt.project.structure.data

trait PathInterpreter[A]:
  /**
   * Only path interpreter instances are allowed to access the path string from [[InterpretablePath]].
   */
  protected given PathInterpreter.UnsafePathStringAccess()

  def interpret(path: InterpretablePath): A

object PathInterpreter:
  class UnsafePathStringAccess private[PathInterpreter] ()
