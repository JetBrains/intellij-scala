public abstract class JClass<T, Z extends String>  {
  public abstract T t();
  public <U> void m() {}
}
/*
abstract class JClass[T, Z <: String] {
  def t: T

  def m[U](): Unit = {
  }
}
 */