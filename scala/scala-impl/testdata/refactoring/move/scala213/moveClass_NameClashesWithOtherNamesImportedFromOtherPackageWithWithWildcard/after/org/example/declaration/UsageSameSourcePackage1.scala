package org.example.declaration

import org.example.declaration.data.{Random, X}

import scala.util._

object UsageSameSourcePackage1 {
  def main(args: Array[String]): Unit = {
    println(this.getClass)
    println(classOf[Random])
    println(Properties.versionString)
    println()

    val x: X = ???
  }
}