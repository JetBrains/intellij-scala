package org.jetbrains.sbt.project.structure.data

trait PathConstructor[A]:
  protected given PathConstructor.UnsafeConstructorAccess()

  def construct(a: A): InterpretablePath

object PathConstructor:
  class UnsafeConstructorAccess private[PathConstructor] ()
