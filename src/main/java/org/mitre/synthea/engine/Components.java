package org.mitre.synthea.engine;

/**
 * Various components used in the generic module framework. All components
 * should be defined within this class.
 */
public abstract class Components {

	/**
	 * A Range of values, with a low and a high. Values must be numeric. (ex,
	 * Integer, Long, Double)
	 * 
	 * @param <R>
	 *            Type of range
	 */
	public static class Range<R extends Number> {

		public Range(R low, R high) {
			this.low = low;
			this.high = high;
		}

		/**
		 * Minimum value of the range.
		 */
		public R low;

		/**
		 * Maximum value of the range.
		 */
		public R high;
	}

	/**
	 * Variant of the Range class, where a unit is required. Defining this in a
	 * separate class makes it easier to define where units are and are not
	 * required.
	 *
	 * @param <R>
	 *            Type of range
	 */
	public static class RangeWithUnit<R extends Number> extends Range<R> {

		public RangeWithUnit(R low, R high, String unit) {
			super(low, high);
			this.unit = unit;
		}

		/**
		 * Unit for the range. Ex, "years" if the range represents an amount of time.
		 */
		public String unit;
	}

	/**
	 * An Exact quantity representing a single fixed value. Note that "quantity"
	 * here may be a bit of a misnomer as the value does not have to be numeric. Ex,
	 * it may be a String or Code.
	 * 
	 * @param <T>
	 *            Type of quantity
	 */
	public static class Exact<T> {

		public Exact(T quantity) {
			this.quantity = quantity;
		}

		/**
		 * The fixed value.
		 */
		public T quantity;
	}

	/**
	 * Variant of the Exact class, where a unit is required. Defining this in a
	 * separate class makes it easier to define where units are and are not
	 * required.
	 * 
	 * @param <T
	 *            >Type of quantity
	 */
	public static class ExactWithUnit<T> extends Exact<T> {

		public ExactWithUnit(T quantity, String unit) {
			super(quantity);
			this.unit = unit;
		}

		/**
		 * Unit for the quantity. Ex, "days" if the quantity represents an amount of
		 * time.
		 */
		public String unit;
	}

	public static class DateInput {
		public int year;
		public int month;
		public int day;
		public int hour;
		public int minute;
		public int second;
		public int millisecond;
	}

}
