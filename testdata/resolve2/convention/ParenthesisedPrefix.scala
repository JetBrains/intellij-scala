object P {
  def unary_+ = 20
  def unary_+(x: Int) = 21
  def unary_+(x: String) = 22
}

println(/*name: unary_+, line: 2 */+P)
println((/*name: unary_+, line: 3 */+P)(3))