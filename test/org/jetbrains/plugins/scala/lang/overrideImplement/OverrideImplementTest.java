package org.jetbrains.plugins.scala.lang.overrideImplement;

import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.annotations.NonNls;
import junit.framework.Test;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil;
import scala.None$;
import scala.Array;
import scala.List;
import scala.collection.mutable.ArrayBuffer;

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
    final int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);
    ScalaFile file = (ScalaFile) TestUtils.createPseudoPhysicalScalaFile(myProject, fileText);
    final ScTypeDefinition clazz = file.getTypeDefinitions()[0];
    final PsiElement method = ScalaOIUtil.getMethod(clazz, methodName, isImplement);
    final Runnable runnable = new Runnable() {
      public void run() {
        clazz.addMember(method, new None$(), offset);
      }
    };
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(myProject, runnable, "test", null);
      }
    });
    return file.getText();
  }

  public static Test suite() {
    return new OverrideImplementTest();
  }
}
