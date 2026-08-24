package org.jetbrains.sbt.project

enum ImportMode:
  case BuiltIn, NewShell, OldShell

  def usesSbtShell: Boolean = this match
    case BuiltIn => false
    case NewShell | OldShell => true

  def displayName: String = this match
    case BuiltIn => "built-in"
    case NewShell => "new shell"
    case OldShell => "old shell"
