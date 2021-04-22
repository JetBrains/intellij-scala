package org.jetbrains.plugins.scala.packageSearch

import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType

object SbtObjects {
  val buildSystemType = new BuildSystemType("SBT", "sbt")
//  val libConfigurations = "compile,test,runtime,integrationtest,default,provided,optional"
  val libConfigurations = "compile,test"
  val defaultLibConfiguration = "compile"
  val configurationTerminology = "configuration"
}
