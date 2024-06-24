package org.mitre.synthea.modules;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class OphthalmicProgressionModule extends Module {

  public OphthalmicProgressionModule() {
    this.name = "OphthalmicProgression";
    this.submoduleName = "OphthalmicProgression";
    this.submodule = true; // make sure this doesn't run except when called
  }

  public Module clone() {
    return this;
  }
  
  /**
   * Constant factor to represent impact of Diabetic Retinopathy stage on Macular thickness.
   * Based on https://pubmed.ncbi.nlm.nih.gov/12510717/
   */
  public static final double DR_THICKNESS_FACTOR = 1;
  
  // https://loinc.org/LL2117-1
  public static final Code[] DR_STAGE_CODES = {
    /* 0 */ new Code("LOINC", "LA18643-9", "No apparent retinopathy"),
    /* 1 */ new Code("LOINC", "LA18644-7", "Mild non-proliferative retinopathy"),
    /* 2 */ new Code("LOINC", "LA18644-7", "Moderate non-proliferative retinopathy"),
    /* 3 */ new Code("LOINC", "LA18646-2", "Severe non-proliferative retinopathy"),
    /* 4 */ new Code("LOINC", "LA18648-8", "Proliferative retinopathy")
  };

  @Override
  public boolean process(Person person, long time) {
    int drStage = (Integer) person.attributes.get("diabetic_retinopathy_stage");
    
    person.attributes.put("diabetic_retinopathy_stage_code", DR_STAGE_CODES[drStage]);
    
    boolean edema = (boolean) person.attributes.getOrDefault("macular_edema", false);

    // set visual acuity in LogMAR

    // some examples (ref: https://en.wikipedia.org/wiki/LogMAR_chart)
    // Foot LogMAR
    // 20/200 1.00
    // 20/160 0.90
    // 20/100 0.70
    // 20/80 0.60
    // 20/40 0.30
    // 20/20 0.00
    // 20/16 −0.10
    if (edema) {
      person.attributes.put("visual_acuity_logmar", 0.30);
    } else {
      person.attributes.put("visual_acuity_logmar", 0.0);
    }

    // set intraocular pressure in mmHG

    boolean highIop = (boolean) person.attributes.getOrDefault("high_iop", false);

    // "Pressures of between 11 and 21 mmHg are considered normal"
    // https://www.ncbi.nlm.nih.gov/books/NBK532237/
    //
    // https://www.mdpi.com/2077-0383/13/3/676
    if (highIop || (drStage == 4 && person.rand() < 0.001)) {
      // very small chance of this happening
      person.attributes.getOrDefault("high_iop", true);

      if (person.record.medicationActive("1923432") || person.record.medicationActive("861204")) {
        // dorzolamide 20 MG/ML / timolol 5 MG/ML Ophthalmic Solution
        // or
        // brimonidine tartrate 1 MG/ML Ophthalmic Solution

        // reduce the pressure to a normal range
        person.attributes.put("intraocular_pressure", (int) person.rand(new int[] { 15, 18 }));
      } else {
        // not on pressure drops, give them a high (but not always > clinical threshold)
        // iop
        person.attributes.put("intraocular_pressure", (int) person.rand(new int[] { 18, 24 }));
      }

    } else {
      // normal
      person.attributes.put("intraocular_pressure", (int) person.rand(new int[] { 14, 18 }));
    }

    // set findings for OCT
    Code noAbnormalFindings = new Code("LOINC", "LA28409-8", "No abnormal findings");
    person.attributes.put("oct_findings_left", noAbnormalFindings);
    person.attributes.put("oct_findings_right", noAbnormalFindings);
    /*
      No abnormal findings   0     LA24809-8
      Atrophy   1     LA24637-3
      Cystoid macular edema   2     LA25465-8
      Disruption in Bruchs membrane   3     LA25463-3
      Epiretinal membrane   4     LA24864-3
      Photoreceptor layer remodeling  5     LA25464-1
      Schisis cysts   6     LA24889-0
      Sub-RPE deposition  7     LA25503-6 
     */

    // https://iovs.arvojournals.org/article.aspx?articleid=2165526
    // another source (not used) https://www.ncbi.nlm.nih.gov/pmc/articles/PMC1941772/
    Integer octOffset = (Integer) person.attributes.get("base_oct_offset");
    if (octOffset == null) {
      octOffset = (int) person.rand(-10, 10);
      if (person.attributes.get(Person.GENDER).equals("M")) {
        // Central subfield thickness was significantly greater in males than that in
        // females (P < 0.001, Table 2),
        // with a mean of 278 ± 23 μm in males and 263 ± 22 μm in females.
        octOffset += 10;
      }
      
      person.attributes.put("base_oct_offset", octOffset);
    }
    
    //
    octOffset += (int)(drStage * DR_THICKNESS_FACTOR);

    person.attributes.put("oct_center_point_thickness", 227 + octOffset);
    person.attributes.put("oct_center_subfield_thickness", 270 + octOffset);
    person.attributes.put("oct_inner_superior_subfield_thickness", 335 + octOffset);
    person.attributes.put("oct_inner_nasal_subfield_thickness", 338 + octOffset);
    person.attributes.put("oct_inner_inferior_subfield_thickness", 332 + octOffset);
    person.attributes.put("oct_inner_temporal_subfield_thickness", 324 + octOffset);
    person.attributes.put("oct_outer_superior_subfield_thickness", 290 + octOffset);
    person.attributes.put("oct_outer_nasal_subfield_thickness", 305 + octOffset);
    person.attributes.put("oct_outer_inferior_subfield_thickness", 280 + octOffset);
    person.attributes.put("oct_outer_temporal_subfield_thickness", 279 + octOffset);
    person.attributes.put("oct_total_volume", 8.4);

    // note return options here, see State$CallSubmodule
    // if we return true, the submodule completed and processing continues to the
    // next state
    // if we return false, the submodule did not complete (like with a Delay)
    // and will re-process the next timestep.
    return true;
  }
}
