()
trait IJTest {
  self : MySub =>
  type FooType
  protected implicit def d: FooType
  /*start*/d/*end*/
}
trait MySub extends IJTest {
  type FooType = Long
}
//IJTest.this.FooType