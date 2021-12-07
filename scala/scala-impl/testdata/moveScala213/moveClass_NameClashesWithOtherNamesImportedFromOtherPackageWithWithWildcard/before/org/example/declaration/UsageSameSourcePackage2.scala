package org.example.declaration

import org.example.declaration.Random
import org.example.declaration.data.{A, B => B_Renamed}

import scala.util._

object UsageSameSourcePackage2 {
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