package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class Bug2Test extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "bug2/"
  }

  def testSCL1717() {doTest()}
  def testSCL1934() {doTest()}
  def testSCL2087() {doTest()}
  def testSCL2268() {doTest()}
  def testSCL2293() {doTest()}
  def testSCL2120() {doTest()}
  def testSCL2295() {doTest()}
  def testSCL2384A() {doTest()}
  def testSCL2384B() {doTest()}
  def testSCL2384C() {doTest()}
  def testSCL2390() {doTest()}
  def testSCL2390B() {doTest()}
  def testSCL2408A() {doTest()}
  def testSCL2408B() {doTest()}
  def testSCL2408C() {doTest()}
  def testSCL2452() {doTest()}
  def testSCL1929() {doTest()}
  def testSCL2418() {doTest()}
  def testSCL2529() {doTest()}
  def testSCL2039() {doTest()}
  def testSCL2722() {doTest()}
  def testSCL2765() {doTest()}
  def testDependent() {doTest()}
  def testDependentEquality() {doTest()}
  def testSCL2904() {doTest()}
  def testSCL2827A() {doTest()}
  def testSCL2827B() {doTest()}
  def testSCL2827C() {doTest()}
  def testSCL2827D() {doTest()}
  def testSCL2868() {doTest()}
  def testSCL2947() {doTest()}
  def testSCL2934() {doTest()}
  def testSCL2996() {doTest()}
  def testSCL2996B() {doTest()}
  def testSCL2714() {doTest()}
  def testSCL3020() {doTest()}
  def testSCL3040() {doTest()}
  def testSCL3104() {doTest()}
  def testSCL3104B() {doTest()}
  def testSCL3142() {doTest()}
  def testSCL3159A() {doTest()}
  def testSCL3159B() {doTest()}
  def testSCL3159C() {doTest()}
  def testSCL3220() {doTest()}
  def testSCL3928() {doTest()}
  def testSCL2407() {doTest()}
  def testSCL3485() {doTest()}
  def testSCL3539() {doTest()}
  def testSCL3546() {doTest()}
  def testSCL1707() = doTest()
  def testSCL2073() = doTest()
  def testSCL2386A() = doTest()
  def testSCL2386B() = doTest()
  def testSCL2386C() = doTest()
  def testSCL2386D() = doTest()
  def testSCL2386E() = doTest()
  def testSCL2456() = doTest()
  def testFromCompanion() = doTest()
  def testValueFunction11() = doTest()
  def testSCL2169() = doTest()
}