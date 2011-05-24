def foo(x: Tuple2[Int, Int] => Int) = 1
foo(/*caret*/)
/*
def foo(x: Tuple2[Int, Int] => Int) = 1
foo((tuple: (Int, Int)) =>/*caret*/)
*/