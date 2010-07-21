def f(p: Object {val smth: Int}) {}
def f(x: Boolean) {}

/* line: 1 */f(new Object {val smth = 123})
/* line: 1 */f(new Object {val smth: Int = 123})