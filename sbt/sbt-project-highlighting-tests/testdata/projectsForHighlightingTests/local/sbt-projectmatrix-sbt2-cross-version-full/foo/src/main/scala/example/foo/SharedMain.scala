package example.foo

object SharedMain {
  val value: String = PlatformMain.value + GeneratedMain.value
}
