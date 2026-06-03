def foo(x: Int): (Int => Int) => Int = _(3) + x

foo(3)(p => /*start*/p/*end*/)
//Int