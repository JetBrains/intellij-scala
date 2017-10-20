def f(a: Int, b: Boolean) {}

/* applicable: false */ f(1, /* offset: 14 */b = true, /* offset: 14 */b = true)