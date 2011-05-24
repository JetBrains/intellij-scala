def foo(x: Tuple2[Int, Int] => Int) = 1
foo{/*caret*/}
/*
def foo(x: Tuple2[Int, Int] => Int) = 1
foo{case (i: Int, i0: Int) =>/*caret*/}
*/