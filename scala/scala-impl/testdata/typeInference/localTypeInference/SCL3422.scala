class Z
implicit object U extends Z

def foo[T <: Z]()(implicit z: T): T = z

/*start*/foo()/*end*/
//U.type