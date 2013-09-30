package tests;

public class Foo {
    public static void main(String[] args) {
        NameAfterRename$.MODULE$.x();

        Class c = NameAfterRename$class.class;

        NameAfterRename b = new NameAfterRename();
        b.baz();
    }
}
