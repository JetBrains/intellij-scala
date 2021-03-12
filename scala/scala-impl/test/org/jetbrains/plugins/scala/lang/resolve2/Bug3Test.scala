package org.jetbrains.plugins.scala.lang.resolve2
import org.jetbrains.plugins.scala.ScalaVersion
import org.junit.Ignore

class Bug3Test_2_11 extends Bug3TestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}
class Bug3Test_2_12 extends Bug3TestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}
class Bug3Test_2_13 extends Bug3TestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  @Ignore // canBuildFrom is not available in 2.13
  override def testSCL7142(): Unit = ()

  // new deprecatedName constructor
  def testSCL18791(): Unit = {doTest()}
}

abstract class Bug3TestBase extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "bug3/"
  }
  def testSCL1707(): Unit = {doTest()}
  def testSCL2073(): Unit = {doTest()}
  def testSCL2109(): Unit = {doTest()}
  def testSCL2116(): Unit = {doTest()}
  def testSCL2386A(): Unit = {doTest()}
  def testSCL2386B(): Unit = {doTest()}
  def testSCL2386C(): Unit = {doTest()}
  def testSCL2386D(): Unit = {doTest()}
  def testSCL2386E(): Unit = {doTest()}
  def testSCL2456(): Unit = {doTest()}
  def testFromCompanion(): Unit = {doTest()}
  def testPathDependent(): Unit = {doTest()}
  def testPathDependent2(): Unit = {doTest()}
  def testPathDependent3(): Unit = {doTest()}
  def testPathDependent4(): Unit = {doTest()}
  def testValueFunction11(): Unit = {doTest()}
  def testSCL2169(): Unit = {doTest()}
  def testSCL2509(): Unit = {doTest()}
  def testSCL2886(): Unit = {doTest()}
  def testSCL3053(): Unit = {doTest()}
  def testSCL3100(): Unit = {doTest()}
  def testSCL3191(): Unit = {doTest()}
  def testSCL3273(): Unit = {doTest()}
  def testSCL3371(): Unit = {doTest()}
  def testSCL3374(): Unit = {doTest()}
  def testSCL3450(): Unit = {doTest()}
  def testSCL3548(): Unit = {doTest()}
  def testSCL3583(): Unit = {doTest()}
  def testSCL3592A(): Unit = {doTest()}
  def testSCL3592B(): Unit = {doTest()}
  def testSCL3592C(): Unit = {doTest()}
  def testSCL3707(): Unit = {doTest()}
  def testSCL3773(): Unit = {doTest()}
  def testSCL3840(): Unit = {doTest()}
  def testSCL3846(): Unit = {doTest()}
  def testSCL3982(): Unit = {doTest()}
  def testSCL3898(): Unit = {doTest()}
  def testSCL3905(): Unit = {doTest()}
  def testSCL3992(): Unit = {doTest()}
  def testSCL4001(): Unit = {doTest()}
  def testSCL4014(): Unit = {doTest()}
  def testSCL4023(): Unit = {doTest()}
  def testSCL4035(): Unit = {doTest()}
  def testSCL4039(): Unit = {doTest()}
  def testSCL4063(): Unit = {doTest()}
  def testSCL4179A(): Unit = {doTest()}
  def testSCL4179B(): Unit = {doTest()}
  def testSCL4200(): Unit = {doTest()}
  def testSCL4347(): Unit = {doTest()}
  def testSCL4390(): Unit = {doTest()}
  def testSCL4393(): Unit = {doTest()}
  def testSCL4399(): Unit = {doTest()}
  def testSCL4529(): Unit = {doTest()}
  def testSCL4684(): Unit = {doTest()}
  def testSCL4697(): Unit = {doTest()}
  def testSCL4891(): Unit = {doTest()}
  def testSCL4961(): Unit = {doTest()}
  def testSCL4987(): Unit = {doTest()}
  def testSCL4993(): Unit = {doTest()}
  def testSCL5107(): Unit = {doTest()}
  def testSCL5145(): Unit = {doTest()}
  def testSCL5245(): Unit = {doTest()}
  def testSCL5245B(): Unit = {doTest()}
  def testSCL5246(): Unit = {doTest()}
  def testSCL5249(): Unit = {doTest()}
  def testSCL5357(): Unit = {doTest()}
  def testSCL5360(): Unit = {doTest()}
  def testSCL5377(): Unit = {doTest()}
  def testSCL5418(): Unit = {doTest()}
  def testSCL5424(): Unit = {doTest()}
  def testSCL5971(): Unit = {doTest()}
  def testSCL5982(): Unit = {doTest()}
  def testSCL5987(): Unit = {doTest()}
  def testSCL6478(): Unit = {doTest()}
  def testSCL6628(): Unit = {doTest()}
  def testSCL6785(): Unit = {doTest()}
  def testSCL6825(): Unit = {doTest()}
  def testSCL6825B(): Unit = {doTest()}
  def testSCL7142(): Unit = {doTest()}
  def testStringInterpolatorPrefix(): Unit = {doTest()}
  def testShadowedImport(): Unit = {doTest()}
  def testSOE(): Unit = {doTest()}
  def testAccessiblePattern(): Unit = {doTest()}
  def testConstructorNamedParameters(): Unit = {doTest()}
  def testSCL9926(): Unit = {doTest()}
  def testSCL11119(): Unit = { doTest() }
  def testSCL10839(): Unit = { doTest() }
  def testSCL10845(): Unit = { doTest() }
  def testSCL10845_1(): Unit = { doTest() }
}
