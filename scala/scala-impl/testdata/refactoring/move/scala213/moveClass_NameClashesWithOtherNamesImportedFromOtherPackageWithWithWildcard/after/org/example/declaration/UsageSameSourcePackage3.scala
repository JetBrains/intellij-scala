package org.example.declaration

import org.example.declaration.data.{Random, _}

import scala.util._

object UsageSameSourcePackage3 {
  def main(args: Array[String]): Unit = {
    println(this.getClass)
    println(classOf[Random])
    println(Properties.versionString)
    println()

    val x: X = ???
    val a: A = ???
    val b: B = ???
  }
}