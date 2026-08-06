package org.jetbrains.sbt
package project.settings

sealed trait Context

object Context {
  object Wizard extends Context

  object Configuration extends Context
}