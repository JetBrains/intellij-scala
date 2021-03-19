package org.jetbrains.plugins.scala.util

object JvmOptions {

  def addOpens(modulePackageList: String*): Seq[String] =
    modulePackageList.flatMap { modulePackage =>
      Seq("--add-opens", s"$modulePackage=ALL-UNNAMED")
    }
}
