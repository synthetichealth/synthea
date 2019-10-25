package org.mitre.synthea.world.geography.quadtree;

public class TestElement implements QuadTreeElement {

  public double xCoordinate;
  public double yCoordinate;

  public TestElement(double x, double y) {
    this.xCoordinate = x;
    this.yCoordinate = y;
  }
  
  @Override
  public double getX() {
    return xCoordinate;
  }

  @Override
  public double getY() {
    return yCoordinate;
  }

  public String toString() {
    return "(" + xCoordinate + ", " + yCoordinate + ")";
  }
}
