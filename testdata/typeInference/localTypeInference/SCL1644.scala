import java.io.File

object SCL1644 extends Application {
  override def main(args: Array[String]) {
    val files: Array[File] = new File("c:/").listFiles()
    /*start*/files.map(file => file.length)/*end*/
  }
}
//Array[Long]