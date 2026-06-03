public class MyClass {
    @Deprecated transient volatile native private String myField1;
    @Deprecated transient volatile native private String myField2;

    public MyClass(String name) {
        this.myField1 = name;
    }
}
/*
class MyClass(@deprecated @volatile @native @transient private var myField1: String) {
  @deprecated
  @volatile
  @native
  @transient private val myField2: String = null
}
*/