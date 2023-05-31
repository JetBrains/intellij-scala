package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp.testkit.gen.UtilGenerators._
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category
import org.junit.Test
import org.scalacheck.Prop.forAll
import org.scalatestplus.scalacheck.Checkers

@Category(Array(classOf[SlowTests]))
class BspUtilProperties extends Checkers {

  @Test
  def stringOpsToUri(): Unit = check(
    forAll(genUri) { uri =>
      uri.toURI.toString == uri
    }
  )

  @Test
  def uriOpsToFile(): Unit = check(
    forAll(genPath) { path =>
      path.toUri.toFile == path.toFile
    }
  )
}
