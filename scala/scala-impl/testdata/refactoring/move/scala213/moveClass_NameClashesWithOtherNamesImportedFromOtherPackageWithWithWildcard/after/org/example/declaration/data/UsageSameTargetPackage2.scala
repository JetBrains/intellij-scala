package org.example.declaration.data

import org.example.declaration.data.{B => B_Renamed}

import scala.util._

object UsageSameTargetPackage2 {
  def main(args: Array[String]): Unit = {
    println(this.getClass)
    println(classOf[Random])
    println(Properties.versionString)
    println()

    val x: X = ???
    val a: A = ???
    val b: B_Renamed = ???
  }
}