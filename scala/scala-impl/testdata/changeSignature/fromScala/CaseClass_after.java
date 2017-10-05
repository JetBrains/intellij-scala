public class CaseClass1Java {
    public void foo() {
        new MyClass(2, '1', true);
        MyClass.apply(2, '1', true);
        MyClass$.MODULE$.apply(2, '1', true);
    }
}
