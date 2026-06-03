class Test(val foo: Map[String, List[Int]]) {
        def m(bar: String) {
                m(/*start*/bar.tail/*end*/)
        }
}
//String