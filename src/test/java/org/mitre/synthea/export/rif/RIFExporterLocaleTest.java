package org.mitre.synthea.export.rif;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

/**
 * The dates RIF writes and reads are read by a machine, so they have to come
 * out the same whatever locale the generator happens to run under. A date
 * pattern with no Locale takes the default one, and the default one picks the
 * month names, the digits and the calendar.
 */
public class RIFExporterLocaleTest {

  /** 2026-11-18T00:00:00Z. */
  private static final long TIMESTAMP = 1794960000000L;

  private static final Locale[] LOCALES = {
      Locale.forLanguageTag("en-US"),
      Locale.forLanguageTag("fr-FR"),
      Locale.forLanguageTag("de-DE"),
      Locale.forLanguageTag("ja-JP"),
      Locale.forLanguageTag("ru-RU"),
      Locale.forLanguageTag("th-TH"),
      Locale.forLanguageTag("ar-SA"),
  };

  /**
   * Runs a call with a given default locale, on a new thread so that the
   * formatter cached in RIFExporter's ThreadLocal is built under that locale
   * rather than reused from an earlier one.
   */
  private static <T> T under(Locale locale, java.util.function.Supplier<T> call)
      throws InterruptedException {
    Locale previous = Locale.getDefault();
    Locale.setDefault(locale);
    try {
      Object[] result = new Object[1];
      Thread thread = new Thread(() -> result[0] = call.get());
      thread.start();
      thread.join();
      @SuppressWarnings("unchecked")
      T value = (T) result[0];
      return value;
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  public void datesAreWrittenTheSameInEveryLocale() throws InterruptedException {
    for (Locale locale : LOCALES) {
      assertEquals(locale.toLanguageTag(), "18-Nov-2026",
          under(locale, () -> RIFExporter.bb2DateFromTimestamp(TIMESTAMP)));
    }
  }

  @Test
  public void datesAreReadTheSameInEveryLocale() throws InterruptedException {
    for (Locale locale : LOCALES) {
      assertEquals(locale.toLanguageTag(), TIMESTAMP,
          (long) under(locale, () -> RIFExporter.parseSimpleDate("20261118")));
    }
  }

}
