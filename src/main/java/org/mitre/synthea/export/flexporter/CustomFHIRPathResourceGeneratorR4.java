package org.mitre.synthea.export.flexporter;

import ca.uhn.fhir.context.BaseRuntimeChildDatatypeDefinition;
import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeCompositeDatatypeDefinition;
import ca.uhn.fhir.context.RuntimePrimitiveDatatypeDefinition;
import ca.uhn.fhir.context.RuntimeResourceBlockDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.ICompositeType;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r4.model.ExpressionNode;
import org.hl7.fhir.r4.model.ExpressionNode.Kind;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.utils.FHIRPathEngine;
import org.mitre.synthea.export.FhirR4;


/**
 * This class can be used to generate resources using FHIRPath expressions.
 *
 * <p>Note that this is an experimental feature and the API is expected to change.
 * Ideally this will be  made version independent in a future release.
 *
 * <p>Modified for the Flexporter:
 *  -- allow adding to an existing resource
 *  -- add class lookup by resourceType
 *  -- add support for BackboneElements
 *  -- swapped constructors, pass in a FhirContext to avoid recreating it for each resource
 *  -- add support for extensions on primitives
 *  -- more advanced java generics
 *      (some functions now take in {@code Class<? extends T>} instead of just T)
 *  -- reformatted per Synthea style guidelines
 *
 *  <p>Original:
 * https://github.com/hapifhir/hapi-fhir/blob/master/hapi-fhir-validation/src/main/java/org/hl7/fhir/common/hapi/validation/validator/FHIRPathResourceGeneratorR4.java
 *
 *
 * @author Marcel Parciak marcel.parciak@med.uni-goettingen.de
 *
 */
public class CustomFHIRPathResourceGeneratorR4<T extends Resource> {

  private FhirContext ctx;
  private FHIRPathEngine engine;
  private Map<String, Object> pathMapping;
  private T resource = null;

  private Object valueToSet = null;
  private Stack<GenerationTier> nodeStack = null;

  /**
   * The GenerationTier summarizes some variables that are needed to create FHIR elements later on.
   */
  class GenerationTier {
    // The RuntimeDefinition of nodes
    public BaseRuntimeElementDefinition<?> nodeDefinition = null;
    // The actual nodes, i.e. the instances that hold the values
    public List<IBase> nodes = new ArrayList<>();
    // The ChildDefinition applied to the parent (i.e. one of the nodes from a lower
    // GenerationTier) to create nodes
    public BaseRuntimeChildDefinition childDefinition = null;
    // The path segment name of nodes
    public String fhirPathName = null;

    public GenerationTier() {}

    public GenerationTier(BaseRuntimeElementDefinition<?> nodeDef, IBase firstNode) {
      this.nodeDefinition = nodeDef;
      this.nodes.add(firstNode);
    }
  }

  /**
   * Default constructor, needs a call to `setMapping` later on in order to generate any
   * Resources.
   */
  public CustomFHIRPathResourceGeneratorR4() {
    this.ctx = FhirR4.getContext();
    this.pathMapping = new HashMap<String, Object>();
    this.engine = new FHIRPathEngine(new HapiWorkerContext(ctx, ctx.getValidationSupport()));
  }

  /**
   * Setter for the FHIRPath mapping Map instance.
   *
   * @param mapping {@code Map<String, Object>} a mapping of FHIRPath to objects
   *     that will be used to create a Resource.
   */
  public void setMapping(Map<String, Object> mapping) {
    this.pathMapping = mapping;
  }

  /**
   * Getter for a generated Resource. null if no Resource has been generated yet.
   *
   * @return T the generated Resource or null.
   */
  public T getResource() {
    return this.resource;
  }

  /**
   * Sets the resource to apply FHIRPath changes to.
   * If not set, calling generateResource will instantiate a new resource.
   */
  public void setResource(T resource) {
    this.resource = resource;
  }

  /**
   * Prepares the internal state prior to generating a FHIR Resource. Called once upon generation at
   * the start.
   *
   * @param resourceClass {@code Class<T>} The class of the Resource that shall be created (an empty
   *        Resource will be created in this method).
   */
  @SuppressWarnings("unchecked")
  private void prepareInternalState(Class<? extends T> resourceClass) {
    if (this.resource == null) {
      this.resource = (T) this.ctx.getResourceDefinition(resourceClass).newInstance();
    }
  }

  /**
   * The generation method that yields a new instance of the given resourceType
   * with every value set in the FHIRPath mapping.
   *
   * @param resourceType String The class name of the Resource that shall be created.
   * @return T a new FHIR Resource instance of the given resource type.
   */
  public T generateResource(String resourceType) {
    try {
      @SuppressWarnings("unchecked")
      Class<T> resourceClass =
          (Class<T>) Class.forName("org.hl7.fhir.r4.model." + resourceType);

      return generateResource(resourceClass);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * The generation method that yields a new instance of class `resourceClass` with every value set
   * in the FHIRPath mapping.
   *
   * @param resourceClass {@code Class<T>} The class of the Resource that shall be created.
   * @return T a new FHIR Resource instance of class `resourceClass`.
   */
  public T generateResource(Class<? extends T> resourceClass) {
    this.prepareInternalState(resourceClass);

    for (String fhirPath : this.sortedPaths()) {
      // prepare the next fhirPath iteration: create a new nodeStack and set the value
      this.nodeStack = new Stack<>();
      this.nodeStack
          .push(new GenerationTier(this.ctx.getResourceDefinition(this.resource), this.resource));
      this.valueToSet = this.pathMapping.get(fhirPath);

      // pathNode is the part of the FHIRPath we are processing
      ExpressionNode pathNode = this.engine.parse(fhirPath);
      while (pathNode != null) {
        switch (pathNode.getKind()) {
          case Name:
            this.handleNameNode(pathNode);
            break;
          case Function:
            this.handleFunctionNode(pathNode);
            break;
          // case Constant:
          // case Group:
          // case Unary:
          default:
            // TODO: unimplmemented, what to do?
            break;
        }
        pathNode = pathNode.getInner();
      }
    }

    this.nodeStack = null;
    return this.resource;
  }

  /*
   * Handling Named nodes
   */

  /**
   * Handles a named node, either adding a new layer to the `nodeStack` when reaching a Composite
   * Node or adding the value for Primitive Nodes.
   *
   * @param fhirPath String the FHIRPath section for the next GenerationTier.
   * @param value String the value that shall be set upon reaching a PrimitiveNode.
   */
  private void handleNameNode(ExpressionNode fhirPath) {
    BaseRuntimeChildDefinition childDef =
        this.nodeStack.peek().nodeDefinition.getChildByName(fhirPath.getName());
    if (childDef == null) {
      // nothing to do
      return;
    }

    // identify the type of named node we need to handle here by getting the runtime
    // definition type
    switch (childDef.getChildByName(fhirPath.getName()).getChildType()) {
      case COMPOSITE_DATATYPE:
        handleCompositeNode(fhirPath);
        break;

      case PRIMITIVE_DATATYPE:
        handlePrimitiveNode(fhirPath);
        break;

      case RESOURCE_BLOCK:
        // TODO: this appears to be where BackboneElements get handled
        handleResourceBlock(fhirPath);
        break;

      // case ID_DATATYPE:
      // case RESOURCE:
      // case CONTAINED_RESOURCE_LIST:
      // case CONTAINED_RESOURCES:
      // case EXTENSION_DECLARED:
      // case PRIMITIVE_XHTML:
      // case PRIMITIVE_XHTML_HL7ORG:
      // case UNDECL_EXT:
      default:
        // TODO: not implemented. What to do?
    }
  }

  /**
   * Handles primitive nodes with regards to the current latest tier of the nodeStack. Sets a
   * primitive value to all nodes.
   *
   * @param fhirPath ExpressionNode segment of the fhirPath that specifies the primitive value to
   *        set.
   */
  private void handlePrimitiveNode(ExpressionNode fhirPath) {
    // Flexporter modification: check if the fhirPath is just the primitive, in which case just set
    // it but if there is more (ie, an extension) then don't set a value and push a generation tier
    GenerationTier nextTier = new GenerationTier();
    // get the name of the FHIRPath for the next tier
    nextTier.fhirPathName = fhirPath.getName();
    // get the child definition from the parent nodePefinition
    nextTier.childDefinition =
        this.nodeStack.peek().nodeDefinition.getChildByName(fhirPath.getName());
    // create a nodeDefinition for the next tier
    nextTier.nodeDefinition = nextTier.childDefinition.getChildByName(nextTier.fhirPathName);

    // Get the primitive type definition from the childDeftinion
    RuntimePrimitiveDatatypeDefinition primitiveTarget =
        (RuntimePrimitiveDatatypeDefinition) nextTier.childDefinition
            .getChildByName(fhirPath.getName());
    for (IBase nodeElement : this.nodeStack.peek().nodes) {
      // add the primitive value to each parent node

      IPrimitiveType<?> primitive;

      List<IBase> existingValues = nextTier.childDefinition.getAccessor().getValues(nodeElement);

      if (fhirPath.getInner() != null && fhirPath.getInner().getKind() == Kind.Name
          && !existingValues.isEmpty()) {
        // there is an extension on this (possibly other scenarios too?)
        // and already a value set, so don't set a new one
        primitive = (IPrimitiveType<?>) existingValues.get(0);

      } else {
        primitive =
            primitiveTarget.newInstance(nextTier.childDefinition.getInstanceConstructorArguments());
        primitive.setValueAsString(String.valueOf(this.valueToSet));
        nextTier.childDefinition.getMutator().addValue(nodeElement, primitive);
      }

      nextTier.nodes.add(primitive);
    }

    // push the created nextTier to the nodeStack
    this.nodeStack.push(nextTier);
  }

  /**
   * Handles a composite node with regards to the current latest tier of the nodeStack. Creates a
   * new node based on fhirPath if none are available.
   *
   * @param fhirPath ExpressionNode the segment of the FHIRPath that is being handled right now.
   */
  private void handleCompositeNode(ExpressionNode fhirPath) {
    GenerationTier nextTier = new GenerationTier();
    // get the name of the FHIRPath for the next tier
    nextTier.fhirPathName = fhirPath.getName();
    // get the child definition from the parent nodePefinition
    nextTier.childDefinition =
        this.nodeStack.peek().nodeDefinition.getChildByName(fhirPath.getName());
    // create a nodeDefinition for the next tier
    nextTier.nodeDefinition = nextTier.childDefinition.getChildByName(nextTier.fhirPathName);

    RuntimeCompositeDatatypeDefinition compositeTarget =
        (RuntimeCompositeDatatypeDefinition) nextTier.nodeDefinition;
    // iterate through all parent nodes
    for (IBase nodeElement : this.nodeStack.peek().nodes) {
      List<IBase> containedNodes = nextTier.childDefinition.getAccessor().getValues(nodeElement);

      if (nextTier.childDefinition instanceof BaseRuntimeChildDatatypeDefinition
          && ((BaseRuntimeChildDatatypeDefinition) nextTier.childDefinition).getDatatype()
              .isInstance(this.valueToSet)) {

        // this lets us work with objects that are not trivially strings, e.g. CodeableConcepts

        // TODO: are there any other implications to this?
        nextTier.childDefinition.getMutator().setValue(nodeElement, (IBase)this.valueToSet);

      } else {
        if (containedNodes.size() > 0) {
          // check if sister nodes are already available
          nextTier.nodes.addAll(containedNodes);
        } else {
          // if not nodes are available, create a new node
          ICompositeType compositeNode = compositeTarget
              .newInstance(nextTier.childDefinition.getInstanceConstructorArguments());
          nextTier.childDefinition.getMutator().addValue(nodeElement, compositeNode);
          nextTier.nodes.add(compositeNode);
        }
      }
    }

    // push the created nextTier to the nodeStack
    this.nodeStack.push(nextTier);
  }

  private void handleResourceBlock(ExpressionNode fhirPath) {
    GenerationTier nextTier = new GenerationTier();
    // get the name of the FHIRPath for the next tier
    nextTier.fhirPathName = fhirPath.getName();
    // get the child definition from the parent nodePefinition
    nextTier.childDefinition =
        this.nodeStack.peek().nodeDefinition.getChildByName(fhirPath.getName());
    // create a nodeDefinition for the next tier
    nextTier.nodeDefinition = nextTier.childDefinition.getChildByName(nextTier.fhirPathName);

    RuntimeResourceBlockDefinition compositeTarget =
        (RuntimeResourceBlockDefinition) nextTier.nodeDefinition;
    // iterate through all parent nodes
    for (IBase nodeElement : this.nodeStack.peek().nodes) {
      List<IBase> containedNodes = nextTier.childDefinition.getAccessor().getValues(nodeElement);
      if (containedNodes.size() > 0) {
        // check if sister nodes are already available
        nextTier.nodes.addAll(containedNodes);
      } else {
        // if not nodes are available, create a new node
        IBase compositeNode =
            compositeTarget.newInstance(nextTier.childDefinition.getInstanceConstructorArguments());
        nextTier.childDefinition.getMutator().addValue(nodeElement, compositeNode);
        nextTier.nodes.add(compositeNode);
      }
    }
    // push the created nextTier to the nodeStack
    this.nodeStack.push(nextTier);
  }

  /*
   * Handling Function Nodes
   */

  /**
   * Handles a function node of a FHIRPath.
   *
   * @param fhirPath ExpressionNode the segment of the FHIRPath that is being handled right now.
   */
  private void handleFunctionNode(ExpressionNode fhirPath) {
    switch (fhirPath.getFunction()) {
      case Where:
        this.handleWhereFunctionNode(fhirPath);
        break;
      // case Aggregate:
      // case Alias:
      // case AliasAs:
      // case All:
      // case AllFalse:
      // case AllTrue:
      // case AnyFalse:
      // case AnyTrue:
      // case As:
      // case Check:
      // case Children:
      // case Combine:
      // case ConformsTo:
      // case Contains:
      // case ConvertsToBoolean:
      // case ConvertsToDateTime:
      // case ConvertsToDecimal:
      // case ConvertsToInteger:
      // case ConvertsToQuantity:
      // case ConvertsToString:
      // case ConvertsToTime:
      // case Count:
      // case Custom:
      // case Descendants:
      // case Distinct:
      // case Empty:
      // case EndsWith:
      // case Exclude:
      // case Exists:
      // case Extension:
      // case First:
      // case HasValue:
      // case HtmlChecks:
      // case Iif:
      // case IndexOf:
      // case Intersect:
      // case Is:
      // case IsDistinct:
      // case Item:
      // case Last:
      // case Length:
      // case Lower:
      // case Matches:
      // case MemberOf:
      // case Not:
      // case Now:
      // case OfType:
      // case Repeat:
      // case Replace:
      // case ReplaceMatches:
      // case Resolve:
      // case Select:
      // case Single:
      // case Skip:
      // case StartsWith:
      // case SubsetOf:
      // case Substring:
      // case SupersetOf:
      // case Tail:
      // case Take:
      // case ToBoolean:
      // case ToChars:
      // case ToDateTime:
      // case ToDecimal:
      // case ToInteger:
      // case ToQuantity:
      // case ToString:
      // case ToTime:
      // case Today:
      // case Trace:
      // case Type:
      // case Union:
      // case Upper:
      default:
        // TODO: unimplemented, what to do?
    }
  }

  /**
   * Handles a function node of a `where`-function. Iterates through all params and handle where
   * functions for primitive datatypes (others are not implemented and yield errors.)
   *
   * @param fhirPath ExpressionNode the segment of the FHIRPath that contains the where function
   */
  private void handleWhereFunctionNode(ExpressionNode fhirPath) {
    // iterate through all where parameters
    for (ExpressionNode param : fhirPath.getParameters()) {
      BaseRuntimeChildDefinition wherePropertyChild =
          this.nodeStack.peek().nodeDefinition.getChildByName(param.getName());
      BaseRuntimeElementDefinition<?> wherePropertyDefinition =
          wherePropertyChild.getChildByName(param.getName());

      // only primitive nodes can be checked using the where function
      switch (wherePropertyDefinition.getChildType()) {
        case PRIMITIVE_DATATYPE:
          this.handleWhereFunctionParam(param);
          break;
        // case COMPOSITE_DATATYPE:
        // case CONTAINED_RESOURCES:
        // case CONTAINED_RESOURCE_LIST:
        // case EXTENSION_DECLARED:
        // case ID_DATATYPE:
        // case PRIMITIVE_XHTML:
        // case PRIMITIVE_XHTML_HL7ORG:
        // case RESOURCE:
        // case RESOURCE_BLOCK:
        // case UNDECL_EXT:
        default:
          // TODO: unimplemented. What to do?
      }
    }
  }

  /**
   * Filter the latest nodeStack tier using `param`.
   *
   * @param param ExpressionNode parameter type ExpressionNode that provides the where clause that
   *        is used to filter nodes from the nodeStack.
   */
  private void handleWhereFunctionParam(ExpressionNode param) {
    BaseRuntimeChildDefinition wherePropertyChild =
        this.nodeStack.peek().nodeDefinition.getChildByName(param.getName());
    BaseRuntimeElementDefinition<?> wherePropertyDefinition =
        wherePropertyChild.getChildByName(param.getName());

    String matchingValue = param.getOpNext().getConstant().toString();
    List<IBase> matchingNodes = new ArrayList<>();
    List<IBase> unlabeledNodes = new ArrayList<>();
    // sort all nodes from the nodeStack into matching nodes and unlabeled nodes
    for (IBase node : this.nodeStack.peek().nodes) {
      List<IBase> operationValues = wherePropertyChild.getAccessor().getValues(node);
      if (operationValues.size() == 0) {
        unlabeledNodes.add(node);
      } else {
        for (IBase operationValue : operationValues) {
          IPrimitiveType<?> primitive = (IPrimitiveType<?>) operationValue;
          switch (param.getOperation()) {
            case Equals:
              if (primitive.getValueAsString().equals(matchingValue)) {
                matchingNodes.add(node);
              }
              break;
            case NotEquals:
              if (!primitive.getValueAsString().equals(matchingValue)) {
                matchingNodes.add(node);
              }
              break;
            case And:
            case As:
            case Concatenate:
            case Contains:
            case Div:
            case DivideBy:
            case Equivalent:
            case Greater:
            case GreaterOrEqual:
            case Implies:
            case In:
            case Is:
            case LessOrEqual:
            case LessThan:
            case MemberOf:
            case Minus:
            case Mod:
            case NotEquivalent:
            case Or:
            case Plus:
            case Times:
            case Union:
            case Xor:
            default:
              // TODO: unimplemented, what to do?
          }
        }
      }
    }

    if (matchingNodes.size() == 0) {
      if (unlabeledNodes.size() == 0) {
        // no nodes were matched and no unlabeled nodes are available. We need to add a
        // sister node to the nodeStack
        GenerationTier latestTier = this.nodeStack.pop();
        GenerationTier previousTier = this.nodeStack.peek();
        this.nodeStack.push(latestTier);

        RuntimeCompositeDatatypeDefinition compositeTarget =
            (RuntimeCompositeDatatypeDefinition) latestTier.nodeDefinition;
        ICompositeType compositeNode = compositeTarget
            .newInstance(latestTier.childDefinition.getInstanceConstructorArguments());
        latestTier.childDefinition.getMutator().addValue(previousTier.nodes.get(0), compositeNode);
        unlabeledNodes.add(compositeNode);
      }

      switch (param.getOperation()) {
        case Equals:
          // if we are checking for equality, we need to set the property we looked for on
          // the unlabeled node(s)
          RuntimePrimitiveDatatypeDefinition equalsPrimitive =
              (RuntimePrimitiveDatatypeDefinition) wherePropertyDefinition;
          IPrimitiveType<?> primitive =
              equalsPrimitive.newInstance(wherePropertyChild.getInstanceConstructorArguments());
          primitive.setValueAsString(param.getOpNext().getConstant().toString());
          for (IBase node : unlabeledNodes) {
            wherePropertyChild.getMutator().addValue(node, primitive);
            matchingNodes.add(node);
          }
          break;
        case NotEquals:
          // if we are checking for inequality, we need to pass all unlabeled (or created
          // if none were available)
          matchingNodes.addAll(unlabeledNodes);
          break;
        case And:
        case As:
        case Concatenate:
        case Contains:
        case Div:
        case DivideBy:
        case Equivalent:
        case Greater:
        case GreaterOrEqual:
        case Implies:
        case In:
        case Is:
        case LessOrEqual:
        case LessThan:
        case MemberOf:
        case Minus:
        case Mod:
        case NotEquivalent:
        case Or:
        case Plus:
        case Times:
        case Union:
        case Xor:
        default:
          // TODO: need to implement above first
      }
    }

    // set the nodes to the filtered ones
    this.nodeStack.peek().nodes = matchingNodes;
  }

  /**
   * Creates a list all FHIRPaths from the mapping ordered by paths with where equals, where
   * unequals and the rest.
   *
   * @return {@code List<String>} a List of FHIRPaths ordered by the type.
   */
  private List<String> sortedPaths() {
    List<String> whereEquals = new ArrayList<String>();
    List<String> whereUnequals = new ArrayList<String>();
    List<String> withoutWhere = new ArrayList<String>();

    for (String fhirPath : this.pathMapping.keySet()) {
      switch (this.getTypeOfFhirPath(fhirPath)) {
        case WHERE_EQUALS:
          whereEquals.add(fhirPath);
          break;
        case WHERE_UNEQUALS:
          whereUnequals.add(fhirPath);
          break;
        case WITHOUT_WHERE:
          withoutWhere.add(fhirPath);
          break;
        default:
          // not sure why checkstyle makes this necessary...
          // the 3 above are the only options in the enum
      }
    }

    List<String> ret = new ArrayList<String>();
    ret.addAll(whereEquals);
    ret.addAll(whereUnequals);
    ret.addAll(withoutWhere);
    return ret;
  }

  /**
   * Returns the type of path based on the FHIRPath String.
   *
   * @param fhirPath String representation of a FHIRPath.
   * @return PathType the type of path supplied as `fhirPath`.
   */
  private PathType getTypeOfFhirPath(String fhirPath) {
    ExpressionNode fhirPathExpression = this.engine.parse(fhirPath);
    while (fhirPathExpression != null) {
      if (fhirPathExpression.getKind() == ExpressionNode.Kind.Function) {
        if (fhirPathExpression.getFunction() == ExpressionNode.Function.Where) {
          for (ExpressionNode params : fhirPathExpression.getParameters()) {
            switch (params.getOperation()) {
              case Equals:
                return PathType.WHERE_EQUALS;
              case NotEquals:
                return PathType.WHERE_UNEQUALS;
              case And:
              case As:
              case Concatenate:
              case Contains:
              case Div:
              case DivideBy:
              case Equivalent:
              case Greater:
              case GreaterOrEqual:
              case Implies:
              case In:
              case Is:
              case LessOrEqual:
              case LessThan:
              case MemberOf:
              case Minus:
              case Mod:
              case NotEquivalent:
              case Or:
              case Plus:
              case Times:
              case Union:
              case Xor:
              default:
                // TODO: need to implement above first
            }
          }
        }
      }
      fhirPathExpression = fhirPathExpression.getInner();
    }
    return PathType.WITHOUT_WHERE;
  }

  /**
   * A simple enum to diffirentiate between types of FHIRPaths in the special use case of generating
   * FHIR Resources.
   */
  public enum PathType {
    WHERE_EQUALS, WHERE_UNEQUALS, WITHOUT_WHERE
  }

  /**
   * Helper function to set a single value on a resource.
   * @param resource Resource to set value on
   * @param fieldPath Path to set value to. May be a nested field
   * @param value Value to set at given path on the resource
   */
  public static <T extends Resource> void setField(T resource, String fieldPath, Object value) {
    CustomFHIRPathResourceGeneratorR4<Resource> generator =
        new CustomFHIRPathResourceGeneratorR4<>();
    generator.setResource(resource);

    HashMap<String,Object> mapping = new HashMap<>();
    mapping.put(fieldPath, value);
    generator.setMapping(mapping);
    generator.generateResource(resource.getClass());
  }
}
