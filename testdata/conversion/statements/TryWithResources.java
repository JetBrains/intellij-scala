import java.io.FileOutputStream;

public class TryWithResourcesTest {
  public TryWithResourcesTest() throws Exception {
    /*start*/
    try (FileOutputStream os = new FileOutputStream("test")) {
      os.write(0);
    }
    try (FileOutputStream oss = new FileOutputStream("test")) {
      oss.write(0);
    } finally {
      System.out.println("In finally");
    }
    /*end*/
  }
}
/*
try {
  val os: FileOutputStream = new FileOutputStream("test")
  try os.write(0)
  finally if (os != null) os.close()
}
try {
  val oss: FileOutputStream = new FileOutputStream("test")
  try oss.write(0)
  finally {
    System.out.println("In finally")
    if (oss != null) oss.close()
  }
}*/