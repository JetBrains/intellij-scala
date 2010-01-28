def foo(x: String => Boolean) = !x("test")

foo(/*start*/!_.isEmpty/*end*/)
//(String) => Boolean