import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;

public class GraphvizTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Ignore("Graphviz test is disabled due to time requirements.")
  @Test
  public void testGraphviz() throws Exception {
    TestHelper.exportOff();
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());
    // String inputPath = getClass().getResource("/modules/submodules").getPath();
    String[] args = null; //{ inputPath };
    final PrintStream original = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final PrintStream print = new PrintStream(out, true);
    System.setOut(print);
    Graphviz.main(args);
    String output = out.toString();
    // Assert.assertTrue(output.contains("Loading"));
    Assert.assertTrue(output.contains("Rendering graphs"));
    Assert.assertTrue(output.contains("Completed"));
    System.setOut(original);
  }

}
