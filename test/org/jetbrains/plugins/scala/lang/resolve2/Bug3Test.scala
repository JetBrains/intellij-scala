package org.jetbrains.plugins.scala.lang.resolve2

class Bug3Test extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "bug3/"
  }
  def testSCL1707() {doTest()}
  def testSCL2073() {doTest()}
  def testSCL2386A() {doTest()}
  def testSCL2386B() {doTest()}
  def testSCL2386C() {doTest()}
  def testSCL2386D() {doTest()}
  def testSCL2386E() {doTest()}
  def testSCL2456() {doTest()}
  def testFromCompanion() {doTest()}
  def testValueFunction11() {doTest()}
  def testSCL2169() {doTest()}
  def testSCL2509() {doTest()}
  def testSCL3773() {doTest()}
  def testSCL3840() {doTest()}
  def testSCL3846() {doTest()}
  def testSCL3898() {doTest()}
  def testSCL3992() {doTest()}
}
