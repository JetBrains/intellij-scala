def f(a: Int, b: Boolean) {}

/* applicable: false */ f(/* offset: 6 */a = 1, /* offset: 14 */b = true, /*resolved: false */c = "foo")