package com.webcohesion.enunciate.modules.jackson1.api.impl;

import com.webcohesion.enunciate.api.datatype.DataTypeReference;
import com.webcohesion.enunciate.api.datatype.Example;
import com.webcohesion.enunciate.api.datatype.Property;
import com.webcohesion.enunciate.api.datatype.Value;
import com.webcohesion.enunciate.facets.FacetFilter;
import com.webcohesion.enunciate.modules.jackson1.model.Member;
import com.webcohesion.enunciate.modules.jackson1.model.ObjectTypeDefinition;
import com.webcohesion.enunciate.modules.jackson1.model.types.JsonClassType;
import com.webcohesion.enunciate.modules.jackson1.model.types.JsonType;
import com.webcohesion.enunciate.modules.jackson1.model.types.KnownJsonType;

import java.util.*;

/**
 * @author Ryan Heaton
 */
public class ObjectDataTypeImpl extends DataTypeImpl {

  private final ObjectTypeDefinition typeDefinition;

  public ObjectDataTypeImpl(ObjectTypeDefinition typeDefinition) {
    super(typeDefinition);
    this.typeDefinition = typeDefinition;
  }

  @Override
  public List<? extends Value> getValues() {
    return null;
  }

  @Override
  public List<? extends Property> getProperties() {
    SortedSet<Member> members = this.typeDefinition.getMembers();
    ArrayList<Property> properties = new ArrayList<Property>(members.size());
    FacetFilter facetFilter = this.typeDefinition.getContext().getContext().getConfiguration().getFacetFilter();
    for (Member member : members) {
      for (Member choice : member.getChoices()) {
        if (!facetFilter.accept(choice)) {
          continue;
        }

        properties.add(new PropertyImpl(choice));
      }
    }

    //sort the properties by name.
    Collections.sort(properties, new Comparator<Property>() {
      @Override
      public int compare(Property o1, Property o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    return properties;
  }

  @Override
  public List<DataTypeReference> getSupertypes() {
    ArrayList<DataTypeReference> supertypes = null;

    JsonType supertype = this.typeDefinition.getSupertype();
    while (supertype != null) {
      if (supertypes == null) {
        supertypes = new ArrayList<DataTypeReference>();
      }

      supertypes.add(new DataTypeReferenceImpl(supertype));
      supertype = supertype instanceof JsonClassType ?
        ((JsonClassType)supertype).getTypeDefinition() instanceof ObjectTypeDefinition ?
          ((ObjectTypeDefinition)((JsonClassType)supertype).getTypeDefinition()).getSupertype()
          : null
        : null;
    }

    return supertypes;
  }

  @Override
  public Example getExample() {
    return new ExampleImpl(this.typeDefinition);
  }
}