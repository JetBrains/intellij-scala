package org.jetbrains.plugins.scala.testingSupport

trait ScalaTestingConfiguration {

  def getTestClassPath: String
  def getTestPackagePath: String
}