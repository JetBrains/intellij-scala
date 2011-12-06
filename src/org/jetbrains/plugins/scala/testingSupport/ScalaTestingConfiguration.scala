package org.jetbrains.plugins.scala.testingSupport

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.12.11
 */

trait ScalaTestingConfiguration {
  def getTestClassPath: String
  def getTestPackagePath: String
}