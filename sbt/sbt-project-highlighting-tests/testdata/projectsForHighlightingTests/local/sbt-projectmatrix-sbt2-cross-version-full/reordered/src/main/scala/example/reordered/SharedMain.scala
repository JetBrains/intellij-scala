package example.reordered

object SharedMain {
  val value: String = PlatformMain.value + GeneratedMain.value
}
