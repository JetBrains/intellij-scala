package foo

// The last import is often marked as unresolved.
// Changing the order of these lines to trigger the unstable highlighting.

// No unresolved errors after removing `with T2`
object `package` extends /* */T1 with /* */T2

trait T1

trait T2