package org.example.declaration

import org.example.declaration.data.{X, Random => Random42}

import scala.util._

object UsageSameSourcePackage1_Renamed {
  def main(args: Array[String]): Unit = {
    println(this.getClass)
    println(classOf[Random42])
    println(Properties.versionString)
    println()

    val x: X = ???
  }
}