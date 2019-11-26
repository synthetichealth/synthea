package org.mitre.synthea.world.geography.quadtree;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple QuadTree class that is optimized for query speed.
 * The query is simplified, because it only takes into account
 * Euclidean distance and does not take into account the
 * elevation, altitude, curvature of the Earth, or features
 * such as roads, mountains, or bodies of water.
 */
public class QuadTree {

  protected boolean isLeaf = true;
  /** maximum amount of data at this node before it splits. */
  private int maxLeafSize = 50;

  /** local data before split. */
  protected List<QuadTreeElement> data = new ArrayList<QuadTreeElement>();

  /* bounding box */
  private double xcoord;
  private double ycoord;
  private double radius;

  /* branch data after split. */
  protected QuadTree[] branches;

  /**
   * Default constructor creates a QuadTree centered around (0, 0)
   * with bounds around the center point of 180.
   */
  public QuadTree() {
    xcoord = 0.0;
    ycoord = 0.0;
    radius = 180.0;
  }

  /**
   * Create a QuadTree centered around (x, y) with a bounds of r.
   * @param x The x-coordinate of the center point.
   * @param y The y-coordinate of the center point.
   * @param r The distance of the bounds around the center point.
   */
  private QuadTree(double x, double y, double r) {
    this.xcoord = x;
    this.ycoord = y;
    this.radius = r;
  }

  /**
   * Check if this QuadTree should contain this item.
   * @param item The item to check.
   * @return True if the item should be within the QuadTree, otherwise false.
   */
  public boolean hasBoundsAround(QuadTreeElement item) {
    return hasBoundsAround(item, 0.0);
  }

  /**
   * Check if this QuadTree should contain this item, given some error
   * (in distance) around the item location.
   * @param item The item to check.
   * @param error The error around the item in distance.
   * @return True if the item should be within the QuadTree, otherwise false.
   */
  public boolean hasBoundsAround(QuadTreeElement item, double error) {
    return ((xcoord + radius) >= (item.getX() - error)) 
        && ((xcoord - radius) <= (item.getX() + error))
        && ((ycoord + radius) >= (item.getY() - error))
        && ((ycoord - radius) <= (item.getY() + error));
  }

  /**
   * Insert an item into this tree.
   * @param item The item to insert.
   * @return True if the item was inserted, false otherwise.
   */
  public boolean insert(QuadTreeElement item) {
    boolean inserted = false;
    if (isLeaf) {
      // this is a leaf node
      if (data.size() < maxLeafSize) {
        // the leaf node is still small, add the item here
        inserted = data.add(item);
      } else {
        // the leaf node is too big, split the leaf here
        isLeaf = false;
        double d = (radius / 2.0);
        branches = new QuadTree[4];
        branches[0] = new QuadTree(xcoord + d, ycoord + d, d);
        branches[1] = new QuadTree(xcoord - d, ycoord + d, d);
        branches[2] = new QuadTree(xcoord + d, ycoord - d, d);
        branches[3] = new QuadTree(xcoord - d, ycoord - d, d);
        inserted = this.insert(item);
      }
    } else {
      // this is a branch node
      for (QuadTree branch : branches) {
        if (branch.hasBoundsAround(item)) {
          inserted = branch.insert(item);
          if (inserted) {
            break;
          }
        }
      }
      // this item does not really fit into the branches,
      // so we might as well store it here. This might happen
      // if the item is exactly on the split point.
      if (!inserted) {
        inserted = data.add(item);
      }
    }
    return inserted;
  }

  /**
   * Query this QuadTree for elements around a given point.
   * @param queryPoint The query point to search around.
   * @param radius The radius to search.
   * @return A non-null list of elements within the radius around the queryPoint.
   */
  public List<QuadTreeElement> query(QuadTreeElement queryPoint, double radius) {
    List<QuadTreeElement> results = new ArrayList<QuadTreeElement>();
    for (QuadTreeElement localItem : data) {
      if (queryPoint.distance(localItem) <= radius) {
        results.add(localItem);
      }
    }
    // if this node has split...
    if (!isLeaf) {
      for (QuadTree branch : branches) {
        if (branch.hasBoundsAround(queryPoint, radius)) {
          results.addAll(branch.query(queryPoint, radius));
        }
      }
    }
    return results;
  }

  /**
   * Get the count of elements within this QuadTree including all branches.
   * @return The count of elements within this QuadTree including all branches.
   */
  public int size() {
    int count = data.size();
    if (!isLeaf) {
      for (QuadTree branch : branches) {
        count += branch.size();
      }
    }
    return count;
  }

  @Override
  public String toString() {
    String value;
    if (isLeaf) {
      value = "Leaf @ (";
    } else {
      value = "Branch @ (";
    }
    value += xcoord + ", " + ycoord + ", " + radius + ") = ";
    value += size();
    return value;
  }
}
