package org.jetbrains.plugins.scala
package base

import org.jetbrains.plugins.scala.util.TestUtils

private object CommonLibrary {
  def apply(name: String, version: TestUtils.ScalaSdkVersion): CommonLibrary = {
    name match {
      case "scalaz" => CommonLibrary("scalaz", TestUtils.getMockScalazLib(version))
      case "slick" => CommonLibrary("slick", TestUtils.getMockSlickLib(version))
      case "spray" => CommonLibrary("spray", TestUtils.getMockSprayLib(version))
      case "cats" => CommonLibrary("cats", TestUtils.getCatsLib(version))
      case "specs2" => CommonLibrary("specs2", TestUtils.getSpecs2Lib(version))
      case "scalacheck" => CommonLibrary("scalacheck", TestUtils.getScalacheckLib(version))
      case "postgresql" => CommonLibrary("postgresql", TestUtils.getPostgresLib(version))
      case _ => throw new IllegalArgumentException(s"Unknown library: $name")
    }
  }
}

private case class CommonLibrary(name: String, path: String)