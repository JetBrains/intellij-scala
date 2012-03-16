class A
class C
class B extends C
class Z[+T]
def goo[A, BB >: A](x: A): Z[BB] = new Z[BB]
val zzzzzz : Z[C] = /*start*/goo(new B)/*end*/
//Z[B]