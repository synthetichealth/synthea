package org.mitre.synthea.export.rif;

import com.google.gson.JsonObject;

import org.mitre.synthea.export.rif.identifiers.PartDContractID;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Represents a fill of a prescription.
 */
class PrescriptionFill implements Comparable<PrescriptionFill> {

  long time;
  HealthRecord.Encounter encounter;
  HealthRecord.Medication medication;
  PartDContractID partDContractID;
  int quantity;
  int days;
  int fillNo;
  String medicationCode;
  int refills = 0;

  PrescriptionFill(long time, HealthRecord.Encounter encounter, HealthRecord.Medication medication,
          String medicationCode, int fillNo, PartDContractID partDContractID,
          long supplyInterval, long end) {
    this.time = time;
    this.encounter = encounter;
    this.medication = medication;
    this.medicationCode = medicationCode;
    this.fillNo = fillNo;
    this.partDContractID = partDContractID;
    if (medication.prescriptionDetails != null && medication.prescriptionDetails.has("refills")) {
      refills = medication.prescriptionDetails.get("refills").getAsInt();
    }
    if (end > time + supplyInterval || fillNo > 1) {
      end = time + supplyInterval;
    }
    initDaysAndQuantity(end);
  }

  public boolean refillsRemaining() {
    return refills - fillNo + 1 > 0;
  }

  private void initDaysAndQuantity(long stopTime) {
    this.days = getDays(stopTime);
    double amountPerDay = 1;
    if (medication.prescriptionDetails != null && medication.prescriptionDetails.has("dosage")) {
      JsonObject dosage = medication.prescriptionDetails.getAsJsonObject("dosage");
      long amount = dosage.get("amount").getAsLong();
      long frequency = dosage.get("frequency").getAsLong();
      long perPeriod = amount * frequency;
      long period = dosage.get("period").getAsLong();
      String units = dosage.get("unit").getAsString();
      long periodTime = Utilities.convertTime(units, period);
      long oneDay = Utilities.convertTime("days", 1);
      if (periodTime < oneDay) {
        amountPerDay = ((double) perPeriod * ((double) oneDay / (double) periodTime));
      } else if (periodTime > oneDay) {
        amountPerDay = ((double) perPeriod / ((double) periodTime / (double) oneDay));
      } else {
        amountPerDay = perPeriod;
      }
      if (amountPerDay == 0) {
        amountPerDay = 1;
      }
    }
    this.quantity = (int) (amountPerDay * days);
    if (this.quantity < 1) {
      this.quantity = 1;
    }
  }

  private int getDays(long stopTime) {
    long medDuration = stopTime - time;
    double calcDays = (double) (medDuration / (1000 * 60 * 60 * 24));
    if (medication.prescriptionDetails != null && medication.prescriptionDetails.has("duration")) {
      JsonObject duration = medication.prescriptionDetails.getAsJsonObject("duration");
      long medQuantity = duration.get("quantity").getAsLong();
      String unit = duration.get("unit").getAsString();
      long durationTime = Utilities.convertTime(unit, medQuantity);
      double durationTimeInDays = (double) (durationTime / (1000 * 60 * 60 * 24));
      if (durationTimeInDays < calcDays) {
        calcDays = durationTimeInDays;
      }
    }
    if (calcDays <= 0) {
      calcDays = 1;
    }
    return (int) calcDays;
  }

  @Override
  public int compareTo(PrescriptionFill o) {
    // This method is only intended to be used to order prescriptions by time.
    // Note that this is inconsistent with PrescriptionEvent.equals, see warnings at
    // https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html
    return Long.compare(time, o.time);
  }
}
