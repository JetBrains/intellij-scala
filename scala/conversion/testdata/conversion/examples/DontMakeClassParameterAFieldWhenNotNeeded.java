public abstract class Base {
    private String name;

    public Base(String name) {
        this.name = name;
    }
}

class Child1 extends Base {
    public Child1(String name) {
        super(name);
    }
}

class Child2 extends Base {
    private final String name;

    public Child2(String name) {
        super(name);
        this.name = name;
    }
}
/*
abstract class Base(private var name: String) {
}

class Child1(name: String) extends Base(name) {
}

class Child2(private val name: String) extends Base(name) {
}
*/