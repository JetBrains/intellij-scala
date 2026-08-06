object SCL2911 {
  def debug(msg: Any) {}
  def debug(format: String, params: Any*) {}

  def main(args: Array[String]) {
    <ref>debug("<< this call is highlighted as an error")
  }
}

