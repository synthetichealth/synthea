package org.mitre.synthea.world.geography.quadtree;

public class TestElement implements QuadTreeElement {

  public double coordinateX;
  public double coordinateY;

  public TestElement(double x, double y) {
    this.coordinateX = x;
    this.coordinateY = y;
  }
  
  @Override
  public double getX() {
    return coordinateX;
  }

  @Override
  public double getY() {
    return coordinateY;
  }

  public String toString() {
    return "(" + coordinateX + ", " + coordinateY + ")";
  }
}
