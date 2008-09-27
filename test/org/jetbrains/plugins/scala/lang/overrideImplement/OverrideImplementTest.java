package org.jetbrains.plugins.scala.lang.overrideImplement;

import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.annotations.NonNls;
import junit.framework.Test;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil;
import scala.None$;
import scala.Array;

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.09.2008
 */
public class OverrideImplementTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/overrideImplement/data";
  private int offset;
  private static final String CARET_MARKER = "<caret>";

  private String removeMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  public OverrideImplementTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  /*
   *  File must be like:
   *  implement (or override) + " " +  methodName
   *  <typeDefinition>
   *  Use <caret> to specify caret position.
   */
  public String transform(String testName, String[] data) throws Exception {
    String text = data[0];
    final int i = text.indexOf("\n");
    String info = text.substring(0, i);
    boolean isImplement = info.split(" ")[0].equals("implement");
    String methodName = info.split(" ")[1];
    String fileText = text.substring(i + 1);
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);
    ScalaFile file = (ScalaFile) PsiFileFactory.getInstance(myProject).createFileFromText("temp.scala" +
        ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), fileText);
    ScTypeDefinition clazz = file.getTypeDefinitions()[0];
    PsiElement method = ScalaOIUtil.getMethod(clazz, methodName, isImplement);
    final Array array = new Array(1);
    array.update(0, offset);
    clazz.addMember(method, new None$(), array.elements().toList().toSeq());
    return file.getText();
  }

  public static Test suite() {
    return new OverredeImplementTest();
  }
}
