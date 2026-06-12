package example

class ModuleA {
  fun produceWarnings() {
    deprecatedApi()

    // Some other compilation warnings
    val numbers = listOf("not actually a number") as List<Int>
    val length = "already non-null"!!.length
    println(numbers)
    println(length)
  }

  @Deprecated("Used intentionally to trigger a compilation warning")
  private fun deprecatedApi() = Unit
}
