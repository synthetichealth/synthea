package org.mitre.synthea.world.geography;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.Map;

import org.mitre.synthea.world.geography.quadtree.QuadTreeElement;

/**
 * Place represents a named place with a postal code and coordinate.
 */
public class Place implements QuadTreeElement, Serializable {
  /** The name of the state. For example, Ohio */
  public String state;
  /** The state abbreviation. For example, OH */
  public String abbreviation;
  /** The name of the place. For example, Columbus */
  public String name;
  /** The postal code. For example, 01001 */
  public String postalCode;
  /** Coordinate of the place. */
  public Point2D.Double coordinate;

  /**
   * Create a new row from a CSV row.
   * @param row from the zip file. Each key is the column header.
   */
  public Place(Map<String,String> row) {
    this.state = row.get("USPS");
    this.abbreviation = row.get("ST");
    this.name = row.get("NAME");
    this.postalCode = row.get("ZCTA5");
    double lat = Double.parseDouble(row.get("LAT"));
    double lon = Double.parseDouble(row.get("LON"));
    this.coordinate = new Point2D.Double(lon, lat);
  }

  /**
   * Check whether or not this Place is in the given state.
   * @param state Name or Abbreviation
   * @return true if they are the same state, otherwise false.
   */
  public boolean sameState(String state) {
    return this.state.equalsIgnoreCase(state)
        || this.abbreviation.equalsIgnoreCase(state);
  }

  @Override
  public double getX() {
    return coordinate.getX();
  }

  @Override
  public double getY() {
    return coordinate.getY();
  }
}
