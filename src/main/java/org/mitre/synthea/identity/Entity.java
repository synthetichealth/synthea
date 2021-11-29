package org.mitre.synthea.identity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Entity {
  private List<Seed> seeds;

  public Entity() {
    this.seeds = new ArrayList<>();
  }

  public List<Seed> getSeeds() {
    return seeds;
  }

  public void setSeeds(List<Seed> seeds) {
    this.seeds = seeds;
  }

  public Seed seedAt(LocalDate date) {
    return seeds.stream().filter(s -> s.getPeriod().contains(date)).findFirst().orElse(null);
  }
}
