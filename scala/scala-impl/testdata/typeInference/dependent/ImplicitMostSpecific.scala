abstract class A
abstract class B extends A
implicit def foo1: A  = sys.exit()
implicit def foo2(implicit s: String): B = sys.exit()

def foo[R](implicit a: A): a.type = sys.exit()

implicit val i: String = "text"

/*start*/foo/*end*/
//B