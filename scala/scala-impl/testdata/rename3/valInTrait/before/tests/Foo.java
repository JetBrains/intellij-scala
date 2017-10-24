package tests;

public class Foo {
    public static void main(String[] args) {
        int b1 = BazInst.bar();
        int b2 = BazInst$.MODULE$.bar();
        new BazClass().bar();
    }
}