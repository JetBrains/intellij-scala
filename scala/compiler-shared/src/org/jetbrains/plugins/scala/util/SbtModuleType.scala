package org.jetbrains.plugins.scala.util

object SbtModuleType {
  val sbtSourceSetModuleType =  "sbtSourceSet"
  /*
  TODO sbtNestedModuleType value is not the best chosen, but to change this name correctly it will be necessary to support
   both names (the old and the new) for a period of time. It is needed because otherwise when the user
   would open the project (without reloading), delete a module in build.sbt file and then do a reload, this module wouldn't be deleted.
   */
  val sbtNestedModuleType = "nestedProject"
}
