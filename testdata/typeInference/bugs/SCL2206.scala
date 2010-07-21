def f[T](p: Object {def doSmth: T}) = p.doSmth
/*start*/f(new Object {def doSmth = 123})/*end*/
//Int