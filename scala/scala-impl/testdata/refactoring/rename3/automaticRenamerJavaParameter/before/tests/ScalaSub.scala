class ScalaSub extends JavaSuper {
    override def foo(a: Int, b: String): Unit = {
        val result = b + a
    }
}

def test(sub: ScalaSub): Unit = {
    sub.foo(a = 10, b = "hello")
}
