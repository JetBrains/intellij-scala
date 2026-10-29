package example.downstream

object SharedMain {
  val value: String = example.upstream.SharedMain.value + PlatformMain.value + GeneratedMain.value
}
