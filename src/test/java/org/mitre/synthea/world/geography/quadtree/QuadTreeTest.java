package org.mitre.synthea.world.geography.quadtree;

import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class QuadTreeTest {

  @Test
  public void testInsertOneMillionElements() {
    Random random = new Random();
    QuadTree tree = new QuadTree();
    int amount = 1000000;
    for (int i = 0; i < amount; i++) {
      double x = (random.nextDouble() * 360.0) - 180.0;
      double y = (random.nextDouble() * 360.0) - 180.0;
      TestElement data = new TestElement(x, y);
      Assert.assertTrue(tree.insert(data));
    }
    Assert.assertFalse(tree.isLeaf);
    Assert.assertEquals(amount, tree.size());
  }

  @Test
  public void testInsertNE() {
    Random random = new Random();
    QuadTree tree = new QuadTree();
    int amount = 1000000;
    for (int i = 0; i < amount; i++) {
      double x = (random.nextDouble() * 180.0);
      double y = (random.nextDouble() * 180.0);
      TestElement data = new TestElement(x, y);
      Assert.assertTrue(tree.insert(data));
    }
    Assert.assertFalse(tree.isLeaf);
    Assert.assertEquals(amount, tree.size());
    Assert.assertEquals(amount, tree.data.size() + tree.branches[0].size());
    Assert.assertTrue(tree.branches[1].isLeaf);
    Assert.assertTrue(tree.branches[2].isLeaf);
    Assert.assertTrue(tree.branches[3].isLeaf);
  }

  @Test
  public void testInsertNW() {
    Random random = new Random();
    QuadTree tree = new QuadTree();
    int amount = 1000000;
    for (int i = 0; i < amount; i++) {
      double x = (random.nextDouble() * -180.0);
      double y = (random.nextDouble() * 180.0);
      TestElement data = new TestElement(x, y);
      Assert.assertTrue(tree.insert(data));
    }
    Assert.assertFalse(tree.isLeaf);
    Assert.assertEquals(amount, tree.size());
    Assert.assertEquals(amount, tree.data.size() + tree.branches[1].size());
    Assert.assertTrue(tree.branches[0].isLeaf);
    Assert.assertTrue(tree.branches[2].isLeaf);
    Assert.assertTrue(tree.branches[3].isLeaf);
  }

  @Test
  public void testInsertSE() {
    Random random = new Random();
    QuadTree tree = new QuadTree();
    int amount = 1000000;
    for (int i = 0; i < amount; i++) {
      double x = (random.nextDouble() * 180.0);
      double y = (random.nextDouble() * -180.0);
      TestElement data = new TestElement(x, y);
      Assert.assertTrue(tree.insert(data));
    }
    Assert.assertFalse(tree.isLeaf);
    Assert.assertEquals(amount, tree.size());
    Assert.assertEquals(amount, tree.data.size() + tree.branches[2].size());
    Assert.assertTrue(tree.branches[0].isLeaf);
    Assert.assertTrue(tree.branches[1].isLeaf);
    Assert.assertTrue(tree.branches[3].isLeaf);
  }

  @Test
  public void testInsertSW() {
    Random random = new Random();
    QuadTree tree = new QuadTree();
    int amount = 1000000;
    for (int i = 0; i < amount; i++) {
      double x = (random.nextDouble() * -180.0);
      double y = (random.nextDouble() * -180.0);
      TestElement data = new TestElement(x, y);
      Assert.assertTrue(tree.insert(data));
    }
    Assert.assertFalse(tree.isLeaf);
    Assert.assertEquals(amount, tree.size());
    Assert.assertEquals(amount, tree.data.size() + tree.branches[3].size());
    Assert.assertTrue(tree.branches[0].isLeaf);
    Assert.assertTrue(tree.branches[1].isLeaf);
    Assert.assertTrue(tree.branches[2].isLeaf);
  }

  @Test
  public void testQueryAgainstOneMillionTightlyPackedElements() {
    // Insert all the data
    Random random = new Random();
    QuadTree tree = new QuadTree();
    int amount = 1000000;
    for (int i = 0; i < amount; i++) {
      double x = (random.nextDouble() * 10.0) - 5.0;
      double y = (random.nextDouble() * 10.0) - 5.0;
      TestElement data = new TestElement(x, y);
      Assert.assertTrue(tree.insert(data));
    }
    Assert.assertFalse(tree.isLeaf);
    Assert.assertEquals(amount, tree.size());

    // Query the data
    QuadTreeElement queryPoint = new TestElement(0, 0);
    double queryRadius = 0.125; // 0.125, 0.25, 0.5, 1.0, 2.0
    List<QuadTreeElement> results;
    while (queryRadius <= 2.0) {
      results = tree.query(queryPoint, queryRadius);
      Assert.assertNotNull(results);
      Assert.assertFalse(results.isEmpty());
      // check the distance
      for (QuadTreeElement resultItem : results) {
        Assert.assertTrue(queryPoint.distance(resultItem) <= queryRadius);
      }
      // progressively widen the query
      queryRadius *= 2.0;
    }
  }
}
