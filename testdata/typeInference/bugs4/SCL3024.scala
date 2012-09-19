object FunctionTypes {
        object a {
                def foo(f: () => List[Int]) = f
                def foo(f: String => List[Int]) = f
        }

        val f = a foo {v => List(1)}
        /*start*/f/*end*/
}
//(String) => List[Int]