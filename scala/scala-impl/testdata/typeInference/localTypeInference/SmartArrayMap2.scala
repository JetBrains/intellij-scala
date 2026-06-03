class Z

class B extends Z

val a: Array[Z] = Array.empty
def z: Array[Z] = /*start*/a.map(z => new B)/*end*/
//Array[Z]