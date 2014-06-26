package org.jetbrains.plugins.scala.lang.formatter.automatic;

import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock;
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.ScalaFormattingRuleMatcher;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.io.IOException;

public class RuleMatchTest extends BaseScalaFileSetTestCase {

  @NonNls
  private static final String DATA_PATH = "/formatter/automatic/data/ruleMatch";

  public RuleMatchTest() throws IOException {
    super(
        System.getProperty("path") != null ?
            System.getProperty("path") :
            (new File(TestUtils.getTestDataPath() + DATA_PATH)).getCanonicalPath()
    );
  }
  public String transform(String testName, String[] data) {

    String testCode = data[0];

    String config = data[1];

    String[] rulesNames = config.split("\\n");

    Project project = getProject();

    PsiFile containingFile = TestUtils.createPseudoPhysicalScalaFile(project, testCode);

    assert(containingFile.getFileType() == ScalaFileType.SCALA_FILE_TYPE);

    ASTNode astNode = containingFile.getNode();
    assert(astNode != null);
    CodeStyleSettings codeStyleSettings = new CodeStyleSettings();
    ScalaBlock block = new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent(), null, codeStyleSettings);

    return ScalaFormattingRuleMatcher.testRuleMatch(block, rulesNames);
  }

  public static Test suite() throws IOException {
    return new RuleMatchTest();
  }

}
