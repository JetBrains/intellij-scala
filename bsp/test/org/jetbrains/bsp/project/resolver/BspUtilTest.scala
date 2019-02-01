package org.jetbrains.bsp.project.resolver

import ch.epfl.scala.bsp.gen.UtilGenerators._
import org.jetbrains.bsp.BspUtil._
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

object BspUtilTest extends Properties("BspUtil functions") {

  property("StringOps.toURI") = forAll(genUri) { uri =>
    uri.toURI.toString == uri
  }

  property("URIOps.toFile") = forAll(genPath) { path =>
    path.toUri.toFile == path.toFile
  }
}
