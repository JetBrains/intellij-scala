package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * https://youtrack.jetbrains.com/issue/SCL-24777/Decompiler-tests-documentation
 *
 * https://youtrack.jetbrains.com/issue/SCL-21078/Text-to-text-tests-for-Scala-libraries
 * https://youtrack.jetbrains.com/issue/SCL-23331/Compiler-to-decompiler-tests
 *
 * Implementation: [[org.jetbrains.plugins.scala.tasty.reader.TreePrinter]]
 * Unit tests:     [[org.jetbrains.plugins.scala.tasty.reader.DecompilerTest3]]
 */
package object scala3 {
  private[scala3] implicit val scalaVersion: ScalaVersion = LatestScalaVersions.Scala_3
}