package org.example.declaration.data

import org.example.declaration.data.{Random => Random42}

import scala.util._

object UsageSameTargetPackage1_Renamed {
  def main(args: Array[String]): Unit = {
    println(this.getClass)
    println(classOf[Random42])
    println(Properties.versionString)
    println()

    val x: X = ???
  }
}