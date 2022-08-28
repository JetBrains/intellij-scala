package org.example.usage

import org.example.declaration.data.{Random, X}

import scala.util._

object UsageOtherPackage1 {
  def main(args: Array[String]): Unit = {
    println(this.getClass)
    println(classOf[Random])
    println(Properties.versionString)
    println()

    org.example.declaration.UsageSameSourcePackage1.main(args)
    org.example.declaration.data.UsageSameTargetPackage1.main(args)

    val x: X = ???
  }
}