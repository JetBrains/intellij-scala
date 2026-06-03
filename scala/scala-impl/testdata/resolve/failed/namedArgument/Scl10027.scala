class Foo {
  private def path(p: String): String = ???   // <<---- existence of this method confuses the plugin
  case class RenameCommand(path: String = "", newName: String = "")
  RenameCommand().copy(<ref>newName = "foo")
}