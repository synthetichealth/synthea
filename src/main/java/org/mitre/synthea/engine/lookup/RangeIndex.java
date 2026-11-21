package org.mitre.synthea.engine.lookup;

import java.io.Serializable;
import java.util.List;

/** Age/time index for clinical transition eligibility rules sharing exact person attributes. */
interface RangeIndex<T> extends Serializable {
  void collect(Integer age, long time, List<LookupTableIndex.Entry<T>> results);
}
