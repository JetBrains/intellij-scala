package org.jetbrains.plugins.scala.testingSupport.test.specs2

/**
 * @author Roman.Shein
 * @since 21.04.2015.
 */
object Specs2Util {
  val acceptanseSpecBase: String = "org.specs2.Specification"

  val unitSpecBase: String = "org.specs2.mutable.Specification"

  val suitePaths: List[String] = List("org.specs2.specification.SpecificationStructure",
    "org.specs2.specification.core.SpecificationStructure")
}
