package org.mitre.synthea.world.geography.quadtree;

public interface QuadTreeElement {
  /** Get the x-coordinate of this element. */
  public double getX();

  /** Get the y-coordinate of this element. */
  public double getY();

  /**
   * Return the distance between this element and another.
   * @param element The element to measure the distance towards.
   * @return The distance.
   */
  public default double distance(QuadTreeElement element) {
    double dx = this.getX() - element.getX();
    double dy = this.getY() - element.getY();
    return Math.sqrt((dx * dx) + (dy * dy));
  }
}
