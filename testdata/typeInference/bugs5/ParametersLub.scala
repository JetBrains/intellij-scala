class K
class A extends K
class B extends K

def foo(x: A, y: B) {
  /*start*/Set(x -> 1, y -> 1)/*end*/
}
//Set[(K, Int)]