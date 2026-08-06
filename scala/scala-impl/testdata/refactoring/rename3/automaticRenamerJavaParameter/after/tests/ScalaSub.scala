class ScalaSub extends JavaSuper {
    override def foo(aa: Int, b: String): Unit = {
        val result = b + aa
    }
}

def test(sub: ScalaSub): Unit = {
    sub.foo(aa = 10, b = "hello")
}
