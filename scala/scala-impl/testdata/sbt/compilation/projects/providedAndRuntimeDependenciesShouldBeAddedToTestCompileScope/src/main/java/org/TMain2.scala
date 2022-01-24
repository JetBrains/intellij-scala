package org

object TMain2 {
  def main(args: Array[String]): Unit = {
    System.out.println(classOf[org.apache.commons.compress.MemoryLimitException])
    System.out.println(classOf[org.apache.commons.math.ArgumentOutsideDomainException])
    System.out.println(this.getClass.getClassLoader.loadClass("org.apache.commons.text.AlphabetConverter"))
  }
}