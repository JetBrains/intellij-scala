package org.example.declaration.data

import org.example.declaration.data

import scala.util._

object UsageSameTargetPackage3 {
  def main(args: Array[String]): Unit = {
    println(this.getClass)
    println(classOf[data.Random])
    println(Properties.versionString)
    println()

    val x: X = ???
    val a: A = ???
    val b: B = ???
  }
}