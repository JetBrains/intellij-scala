class SCL2292 {
  self =>
  class Inner {}
  val x: Inner = new Inner
  def m(p: Inner) {}

  /*start*/(new self.Inner, self.x)/*end*/
}
//(SCL2292.this.Inner, SCL2292.this.Inner)