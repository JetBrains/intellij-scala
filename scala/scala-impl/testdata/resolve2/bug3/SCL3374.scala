trait AAA {
  def a
}

Some.apply(_./*resolved: true */a): Option[AAA => Unit] // green
Some.apply(param => param./*resolved: true */a): Option[AAA => Unit] // red