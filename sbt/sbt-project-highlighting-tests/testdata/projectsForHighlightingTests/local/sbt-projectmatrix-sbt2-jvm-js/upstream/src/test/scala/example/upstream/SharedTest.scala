package example.upstream

object SharedTest {
  val value: String = SharedMain.value + PlatformTest.value + GeneratedTest.value
}
