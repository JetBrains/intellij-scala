object Test {
  class A
  object dsl {
    object A { //If you move this object outside dsl object all is ok
    def apply(): A = new A()
      def apply(value: Boolean): A = new A() //If you remove this apply def all is ok
    }
  }
  var myA = dsl A ()   //If you do dsl.A() all is ok
  myA = new A
  /*start*/myA/*end*/
}
//Test.A