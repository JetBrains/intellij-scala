class In[-T]

object SCL9892 {
  def foo[T](t1: In[T], t2: In[T]) = println("f1")
  def foo[T](t1: In[T], t2: T) = println("f2")

  def main(args: Array[String]): Unit = {
    val in = new In[Any]
    <ref>foo(in, in)
  }
}