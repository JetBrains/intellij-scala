object SCL9719 {
  implicit class Pimp(val x: String) extends AnyVal {
    def extended = x.reverse
  }

  val pimp = <ref>Pimp
}