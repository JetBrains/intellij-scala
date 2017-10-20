object SCL5790 {
  import scala.reflect.api.Universe

  class Test[U <: Universe](val u: U) {
    def someMethod: u.Type = ???
  }

  class AnotherTest[U <: Universe](val u: U) {
    val test = new Test[u.type](/*start*/u/*end*/)
    def anotherMethod: u.Type = test.someMethod
  }
}
//AnotherTest.this.u.type