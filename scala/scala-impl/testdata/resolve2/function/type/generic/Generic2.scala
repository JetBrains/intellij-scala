def f(a: List[Int], b: Int) {}
def f(a: List[String], b: String) {}

println(/* offset: 4 */f(List[Int](), 1))
println(/* offset: 35 */f(List[String](), ""))
