trait Sub{
  type Z = Long
}
val s: Sub = null
val a: s.Z = 1L

val x: AnyRef = a
// False