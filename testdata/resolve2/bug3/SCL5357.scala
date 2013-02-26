trait Test {
  trait SettingValue {
    def value: Any = 0
  }

  type Setting <: SettingValue
  type S1 = Setting
  val x: S1 {} = ???

  // all work now
  def bar(x: Setting {}) = x./* line: 3 */value
  def bar2(x: S1 {}) = x./* line: 3 */value
  def bar3(x: x.type) = x./* line: 3 */value

  // okay
  def foo(x: SettingValue {}) = x./* line: 3 */value
  def foo(x: Setting) = x./* line: 3 */value

}