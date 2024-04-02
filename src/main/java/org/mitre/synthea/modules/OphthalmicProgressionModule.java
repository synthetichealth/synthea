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
	
  @Override
  public boolean process(Person person, long time) {    
    // set visual acuity in LogMAR
    
    // some examples (ref: https://en.wikipedia.org/wiki/LogMAR_chart)
    //  Foot    LogMAR
    //  20/200  1.00
    //  20/160  0.90
    //  20/100  0.70
    //  20/80   0.60
    //  20/40   0.30
    //  20/20   0.00
    //  20/16   −0.10
    person.attributes.put("visual_acuity_logmar", 0.0);

   
    // set intraocular pressure in mmHG
    person.attributes.put("intraocular_pressure", 16);
    
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
    
    
    person.attributes.put("oct_center_point_thickness", 10);
    person.attributes.put("oct_center_subfield_thickness", 10);
    person.attributes.put("oct_inner_superior_subfield_thickness", 10);
    person.attributes.put("oct_inner_nasal_subfield_thickness", 10);
    person.attributes.put("oct_inner_inferior_subfield_thickness", 10);
    person.attributes.put("oct_inner_temporal_subfield_thickness", 10);
    person.attributes.put("oct_outer_superior_subfield_thickness", 10);
    person.attributes.put("oct_outer_nasal_subfield_thickness", 10);
    person.attributes.put("oct_outer_inferior_subfield_thickness", 10);
    person.attributes.put("oct_outer_temporal_subfield_thickness", 10);
    person.attributes.put("oct_total_volume", 10);
    
    
    // note return options here, see State$CallSubmodule
    // if we return true, the submodule completed and processing continues to the next state
    // if we return false, the submodule did not complete (like with a Delay)
    // and will re-process the next timestep.
    return true;
  }
}
