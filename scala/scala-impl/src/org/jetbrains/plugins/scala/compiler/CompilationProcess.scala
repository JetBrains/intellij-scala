package org.jetbrains.plugins.scala
package compiler

/**
 * Nikolay.Tropin
 * 2014-10-07
 */
trait CompilationProcess {
  def run(): Unit

  def stop(): Unit

  def addTerminationCallback(callback: => Unit): Unit
}
