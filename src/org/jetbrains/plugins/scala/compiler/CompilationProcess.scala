package org.jetbrains.plugins.scala
package compiler

/**
 * Nikolay.Tropin
 * 2014-10-07
 */
trait CompilationProcess {
  def run()

  def stop()

  def addTerminationCallback(callback: => Unit)
}
