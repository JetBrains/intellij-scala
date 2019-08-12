package org.jetbrains.plugins.scala.testingSupport

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.12.11
 */
// TODO: remove this redundant class, it is bound to coverage module and makes sense only for it
trait ScalaTestingConfiguration {
  def getTestClassPath: String
  def getTestPackagePath: String
}