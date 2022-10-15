public interface MyInterface<T1> {
    String foo1();
    T1 foo2();
    <T2> T2 foo3();
}

public interface MyAbstractClass<T1> {
    abstract public String foo1();
    abstract public T1 foo2();
    abstract public <T2> T2 foo3();
}

/*
trait MyInterface[T1] {
  def foo1: String

  def foo2: T1

  def foo3[T2]: T2
}

trait MyAbstractClass[T1] {
  def foo1: String

  def foo2: T1

  def foo3[T2]: T2
}
*/