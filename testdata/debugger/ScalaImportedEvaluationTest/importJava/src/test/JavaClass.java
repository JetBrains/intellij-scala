package test;

public class JavaClass {

    public static int staticField = 0;

    public static String staticMethod() {
        return "foo";
    }

    public String instanceField = "bar";

    public int instanceMethod() {
        return 1;
    }

    public class JavaInner {
        public String innerField = "inner " + instanceField;
    }
}