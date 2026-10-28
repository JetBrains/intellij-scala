package example.upstream

object SharedMain {
  val value: String = PlatformMain.value + GeneratedMain.value
}
