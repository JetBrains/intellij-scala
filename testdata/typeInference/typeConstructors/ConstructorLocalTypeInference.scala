class A[T, H](x: T, f: T => H)
def foo[T, H](a: A[T, H]): T = null


foo(/*start*/new A("", {s: String => s.length})/*end*/)
//A[String, Int]