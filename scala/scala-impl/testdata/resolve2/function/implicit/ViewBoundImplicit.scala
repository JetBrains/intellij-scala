def f(implicit f: Nothing => Int) = {}

def g[T <% Int] {
  println(/* offset: 4 */ f)
}
