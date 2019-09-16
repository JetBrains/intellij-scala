object Test {
  def global(a: Int, b: Float) {}

  def withDefault(c: Int = 1, d: String = "aaa") {}

  def call() {
    global(b = 2.2F, a = 2)
    withDefault(d = "bbb")
  }
}