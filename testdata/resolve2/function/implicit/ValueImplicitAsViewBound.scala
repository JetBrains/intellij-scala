def f[X <% T] = {}

trait T
implicit val IntToT: Int => T = _ => new T {}

println(/* offset: 4 */ f[Int])