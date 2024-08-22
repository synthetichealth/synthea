# Java Src Code

### App.java

The engine behind the simualtion is built in Java and can be seen in `src/main/java`

```
├── src/main/java 
|   ├── App.java 
```

The App class provides a command-line interface to run the Synthea generation engine. It handles various command-line arguments to configure the simulation, including options for seeding, population size, date ranges, and other settings.  The class includes methods to display usage information and to parse and validate the command-line arguments. It then uses these arguments to configure and run the Synthea generator, which simulates patient health records.

## Main code structure

The rest of the Java code has the following structure and documentation is provided mostly through in-line comments.  

```
├── src/main/java 
|   ├── App.java 
|   |   ├── org/mitre/synthea
|   |   |   ├── editors
|   |   |   ├── engine
|   |   |   ├── export
|   |   |   ├── helpers
|   |   |   |   ├── physiology
|   |   |   ├── identity
|   |   |   ├── modules
|   |   |   ├── world
|   |   |   |   ├── agents
|   |   |   |   |   ├── behaviors
|   |   |   |   ├── concepts
|   |   |   |   ├── geography
|   |   |   |   |   ├── quadtree
```

A couple of key files to note include

### engine/Generator.java
The Generator class is responsible for simulating the creation of a population of people and their health records over time. The population is generated according to configurable options such as population size, seed for random number generation, location, and demographic criteria. The Generator supports multithreaded execution to speed up the simulation process.

### engine/HealthRecordEditor.java
The HealthRecordEditor offers an interface that can be implemented to modify a Synthea Person's HealthRecord. At the end of every time step in the simulation, the Synthea framework will invoke the shouldRun method. If the shouldRun function returns true, the framework will then invoke the process method. The process method will be passed any encounters that were created in the past time step.

HealthRecordEditors are intended to simulate actions that happen to an individual's health record. This includes loss or corruption of information through user entry error or information system defects.

HealthRecordEditors SHOULD NOT be used to simulate clinical interactions on the underlying physical state / circumstances of the Synthea Person. Those should be implemented in Synthea modules.

### engine/Module.java
 Module represents the entry point of a generic module.
 
 The `modules` map is the static list of generic modules. It is loaded once per process, and the list of modules is shared between the generated population. Because we share modules across the population, it is important that States are cloned before they are executed. This keeps the "master" copy of the module clean.

### engine/State.java
The `State` class represents an abstract base class for different types of states in a simulation module within the Synthea framework. Each state represents a distinct event or action that occurs to a person (e.g., an encounter, condition onset, procedure, etc.) as part of the simulation of their healthcare journey.
 
States define the logic for what happens to the person when they are processed by the simulation engine, including transitions to subsequent states based on conditions, time delays, and other factors. Subclasses of `State` implement specific behaviors and actions, such as recording a medical condition or administering a medication. 
 
### engine/Transition.java
Transition represents all the transition types within the generic module framework.

### identity/Entity.java
This class represents a set of desired demographic information about a Person to be simulated. Typically in Synthea, a person's demographic information is made via random weighted selections and an individual stays in the same place for their entire life. An Entity can be used to specify demographic information. Additionally, information can be supplied that allows the simulation to mimic someone moving their primary place of residence.

This class contains basic-level demographic information, such as date of birth and gender. More detailed information is contained in Seeds. Each Entity is made up of a list of Seeds, which represent the demographic information for a Person over a specified time range.
 
As an example, a Person can have a seed to represent their birthplace. 10 years later, their family moves, so another seed would be added to their record reflecting their new address
 
Seeds have one or more Variants. This is a representation of have the demographic information will be when placed in the exported health record. It can be used to represent data errors or variations typically seen in demographic information, such as nicknames, typos, old addresses, etc.
 
### modules/
Various java files for some of the default modules including `BloodPressureValueGenerator`, `DeathModule`, and the `EncounterModule` which defines the threshold for which a patient seeks symptom-driven care.

### world/agents/
Files for creating new clinicians, persons and providers.

### world/geography
Ingests the geography information for locations and places.