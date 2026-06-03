def f(a: Int, b: Boolean) {}

/* applicable: false */ f(1, true, /* offset: 6 */a = 1, /* offset: 14 */b = true)