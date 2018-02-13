import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.TestHelper;

public class AppTest {

  @Test
  public void testApp() throws Exception {
    TestHelper.exportOff();
    String[] args = {"-s", "0", "-p", "3", "Massachusetts", "Bedford"};
    final PrintStream original = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final PrintStream print = new PrintStream(out, true);
    System.setOut(print);
    App.main(args);
    String output = out.toString();
    Assert.assertTrue(output.contains("Loading"));
    Assert.assertTrue(output.contains("Running with options:"));
    Assert.assertTrue(output.contains("Population:"));
    Assert.assertTrue(output.contains("Seed:"));
    Assert.assertTrue(output.contains("Location:"));
    Assert.assertTrue(output.contains("alive=3"));
    Assert.assertTrue(output.contains("dead"));
    System.setOut(original);
  }
  
  @Test
  public void testInvalidArgs() throws Exception {
    String[] args = {"-s", "foo", "-p", "foo", "Massachusetts", "Bedford"};
    final PrintStream original = System.out;
    final PrintStream originalErr = System.err;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final PrintStream print = new PrintStream(out, true);
    System.setOut(print);
    System.setErr(print);
    App.main(args);
    String output = out.toString();
    Assert.assertTrue(output.contains("Usage"));
    Assert.assertFalse(output.contains("Running with options:"));
    System.setOut(original);
    System.setErr(originalErr);
  }

}
