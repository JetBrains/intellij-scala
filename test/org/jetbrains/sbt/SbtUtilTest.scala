package org.jetbrains.sbt

import com.intellij.testFramework.UsefulTestCase

/**
  * Created by jast on 2017-03-08.
  */
class SbtUtilTest extends UsefulTestCase {

  import SbtUtil._
  def testCompare(): Unit = {
    assert(versionCompare("0.13.13", "0.12.2") > 0)
    assert(versionCompare("0.13.13", "0.13.5") > 0)

    assert(versionCompare("0.12.1", "0.12.32") < 0)
    assert(versionCompare("0.12.1", "0.12.2") < 0)

    assert(versionCompare("1.0.0-SNAPSHOT", "0.13.1232") > 0)
  }

}
