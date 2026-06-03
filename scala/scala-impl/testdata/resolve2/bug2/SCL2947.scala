object A {
  def a = 0
}

implicit def double2A(a: Double) = A

// weak conformance should allow use of double2A as an implicit view.
1./*line: 2*/a