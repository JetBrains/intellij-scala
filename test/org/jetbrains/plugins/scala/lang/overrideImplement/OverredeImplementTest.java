package org.jetbrains.plugins.scala.lang.overrideImplement;

import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.annotations.NonNls;
import junit.framework.Test;
import com.intellij.psi.PsiFileFactory;

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.09.2008
 */
public class OverredeImplementTest extends BaseScalaFileSetTestCase{
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/lexer/";

  public OverredeImplementTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  /*
   *  File must be like:
   *  implement (or override) methodName
   *  <typeDefinition>
   *  Use <caret> to specify caret position.
   */
  public String transform(String testName, String[] data) throws Exception {
   String text = data[0];
   ScalaFile file = (ScalaFile) PsiFileFactory.getInstance(myProject).createFileFromText("temp.scala" +
       ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text);
    ScTypeDefinition t = file.getTypeDefinitions()[0];
    return "";
  }

  public static Test suite() {
    return new OverredeImplementTest();
  }
}
