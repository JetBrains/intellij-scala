def f(implicit p: Int) = {}

def g(implicit c: Int) {
  println(/* offset: 4 */ f)
}
