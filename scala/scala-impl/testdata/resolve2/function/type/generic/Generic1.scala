def f(p: List[Int]) {}
def f(p: List[String]) {}

println(/* resolved: false */f(List[Int]()))
println(/* resolved: false */f(List[String]()))
