object SCL7503 {

  object Test extends App {
    val foo = new MyClass
    /*start*/foo.`type` = "bar"/*end*/
    println(foo.`type`)
  }

  class MyClass {
    var _type: String = null

    def `type`: String = _type

    def type_=(t: String): String = {
      _type = t
      t
    }
  }

}
//String