package tests;

public class Foo {
    public static void main(String[] args) {
        a.x();
        a$.MODULE$.x();

        a b = new a();
        b.baz();
    }
}
