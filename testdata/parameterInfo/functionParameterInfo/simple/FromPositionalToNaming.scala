def foo(x: Int, y: Int, z: Int, u: Int) = x + y + z + u

foo(23, 33, <caret>u = 44, z = 77)
//x: Int, y: Int, [u: Int], [z: Int]