import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;


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
    out.flush();
    String output = out.toString();
    Assert.assertTrue(output.contains("Running with options:"));
    Assert.assertTrue(output.contains("Population:"));
    Assert.assertTrue(output.contains("Seed:"));
    Assert.assertTrue(output.contains("Location:"));
    Assert.assertTrue(output.contains("alive=3"));
    Assert.assertTrue(output.contains("dead="));
    Assert.assertTrue(output.contains("Location: Bedford, Massachusetts"));
    System.setOut(original);
  }

  @Test
  public void testAppWithGender() throws Exception {
    TestHelper.exportOff();
    String[] args = {"-s", "0", "-p", "4", "-g", "M"};
    final PrintStream original = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final PrintStream print = new PrintStream(out, true);
    System.setOut(print);
    App.main(args);
    out.flush();
    String output = out.toString();
    Assert.assertTrue(output.contains("Running with options:"));
    Assert.assertTrue(output.contains("Gender: M"));
    Assert.assertTrue(output.contains("Seed:"));
    Assert.assertTrue(output.contains("alive=4"));
    Assert.assertTrue(output.contains("dead="));
    Assert.assertFalse(output.contains("y/o F"));
    Assert.assertTrue(output.contains("Location: " + Generator.DEFAULT_STATE));
    System.setOut(original);
  }

  @Test
  public void testAppWithAges() throws Exception {
    TestHelper.exportOff();
    String[] args = {"-s", "0", "-p", "3", "-a", "30-39"};
    final PrintStream original = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final PrintStream print = new PrintStream(out, true);
    System.setOut(print);
    App.main(args);
    out.flush();
    String output = out.toString();
    Assert.assertTrue(output.contains("Running with options:"));
    Assert.assertTrue(output.contains("Seed:"));
    Assert.assertTrue(output.contains("alive=3"));
    Assert.assertTrue(output.contains("Location: " + Generator.DEFAULT_STATE));
    String regex = "(.\n)*(3[0-9] y/o)(.\n)*";
    Assert.assertTrue(Pattern.compile(regex).matcher(output).find());
    regex = "(.\n)*(\\(([0-9]|[0-2][0-9]|[4-9][0-9]) y/o)(.\n)*";
    Assert.assertFalse(output.matches(regex));
    System.setOut(original);
  }


  @Test
  public void testAppWithDifferentLocation() throws Exception {
    TestHelper.exportOff();
    String[] args = {"-s", "0", "-p", "3", "Utah", "Salt Lake City"};
    final PrintStream original = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final PrintStream print = new PrintStream(out, true);
    System.setOut(print);
    App.main(args);
    out.flush();
    String output = out.toString();
    Assert.assertTrue(output.contains("Running with options:"));
    Assert.assertTrue(output.contains("Seed:"));
    Assert.assertTrue(output.contains("alive=3"));
    Assert.assertTrue(output.contains("Location: Salt Lake City, Utah"));
    System.setOut(original);
  }

  @Test
  public void testAppWithOverflow() throws Exception {
    TestHelper.exportOff();
    String[] args = {"-s", "1", "-p", "3", "-o", "false"};
    final PrintStream original = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final PrintStream print = new PrintStream(out, true);
    System.setOut(print);
    App.main(args);
    out.flush();
    String output = out.toString();
    Assert.assertTrue(output.contains("Running with options:"));
    Assert.assertTrue(output.contains("Seed:"));
    String regex = "\\{alive=(\\d+), dead=(\\d+)\\}";
    Matcher matches = Pattern.compile(regex).matcher(output);
    Assert.assertTrue(matches.find());
    int alive = Integer.parseInt(matches.group(1));
    int dead = Integer.parseInt(matches.group(2));
    Assert.assertEquals(alive + dead, 3);
    System.setOut(original);
  }

  @Test
  public void testAppWithModuleFilter() throws Exception {
    TestHelper.exportOff();
    Config.set("test_key", "pre-test value");
    String[] args = {"-s", "0", "-p", "0", "-m", "copd" + File.pathSeparator + "allerg*"};
    final PrintStream original = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final PrintStream print = new PrintStream(out, true);
    System.setOut(print);
    App.main(args);
    out.flush();
    String output = out.toString();
    Assert.assertTrue(output.contains("Running with options:"));
    Assert.assertTrue(output.contains("Seed:"));
    Assert.assertTrue(output.contains("Modules:"));
    Assert.assertTrue(output.contains("COPD Module"));
    Assert.assertTrue(output.contains("Allergic"));
    Assert.assertTrue(output.contains("Allergies"));
    Assert.assertFalse(output.contains("asthma"));
    System.setOut(original);
  }

  @Test
  public void testAppWithConfigSetting() throws Exception {
    TestHelper.exportOff();
    Config.set("test_key", "pre-test value");
    String[] args = {"-s", "0", "-p", "0",
        "--test_key", "changed value", "--exporter.fhir.export=true"};
    App.main(args);
    
    Assert.assertEquals("changed value", Config.get("test_key"));
    Assert.assertEquals("true", Config.get("exporter.fhir.export"));
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
    out.flush();
    String output = out.toString();
    Assert.assertTrue(output.contains("Usage"));
    Assert.assertFalse(output.contains("Running with options:"));
    System.setOut(original);
    System.setErr(originalErr);
  }

}
