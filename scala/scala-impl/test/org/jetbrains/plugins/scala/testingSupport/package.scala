package org.jetbrains.plugins.scala

package object testingSupport {

  sealed trait TestLocation
  final case class CaretLocation(fileName: String, line: Int, column: Int) extends TestLocation
  final case class PackageLocation(packageName: String) extends TestLocation
  final case class ModuleLocation(moduleName: String) extends TestLocation
}
