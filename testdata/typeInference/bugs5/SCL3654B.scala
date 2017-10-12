case class Foo(x: Int)

List(1,2,3) map {new Foo(_)}    //works
List(1,2,3) map /*start*/Foo/*end*/             //"map Foo" marked red
//Int => Foo