trait Context {
  class Foo
}

def foo[C: /*line: 6*/c./*line: 2*/Foo](
    c: Context
)