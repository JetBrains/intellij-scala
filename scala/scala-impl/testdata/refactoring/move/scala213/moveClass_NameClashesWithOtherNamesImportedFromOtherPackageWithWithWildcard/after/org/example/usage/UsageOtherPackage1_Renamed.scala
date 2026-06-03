package org.example.usage

import org.example.declaration.data.{X, Random => Random42}

import scala.util._

object UsageOtherPackage1_Renamed {
  def main(args: Array[String]): Unit = {
    println(this.getClass)
    println(classOf[Random42])
    println(Properties.versionString)
    println()

    org.example.declaration.UsageSameSourcePackage1.main(args)
    org.example.declaration.data.UsageSameTargetPackage1.main(args)

    val x: X = ???
  }
}