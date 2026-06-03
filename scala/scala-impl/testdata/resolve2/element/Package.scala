package somepkg

class C {
  println(/* resolved: false */ somepkg.getClass)
  println(classOf[ /* resolved: false */ somepkg])
}