package org.mitre.synthea.helpers;

/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Modified extensively for the Synthea project, to no longer require registration of subtypes,
 * but only allow subtypes defined within the same class file. (ex. Class1$Subclass2)
 * Original source:
 * https://github.com/google/gson/blob/86d88c32cf6a6b7a6e0bbc855d76e4ccf6f120bb/extras/src/main/java/com/google/gson/typeadapters/RuntimeTypeAdapterFactory.java
 */

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

import org.apache.commons.lang3.NotImplementedException;

/**
 * Adapts values whose runtime type may differ from their declaration type. This
 * is necessary when a field's type is not the same type that GSON should create
 * when deserializing that field. For example, consider these types:
 * <pre>   {@code
 *   abstract class Shape {
 *     int x;
 *     int y;
 *   }
 *   class Circle extends Shape {
 *     int radius;
 *   }
 *   class Rectangle extends Shape {
 *     int width;
 *     int height;
 *   }
 *   class Diamond extends Shape {
 *     int width;
 *     int height;
 *   }
 *   class Drawing {
 *     Shape bottomShape;
 *     Shape topShape;
 *   }
 * }</pre>
 * 
 * <p>Without additional type information, the serialized JSON is ambiguous. Is
 * the bottom shape in this drawing a rectangle or a diamond? <pre>   {@code
 *   {
 *     "bottomShape": {
 *       "width": 10,
 *       "height": 5,
 *       "x": 0,
 *       "y": 0
 *     },
 *     "topShape": {
 *       "radius": 2,
 *       "x": 4,
 *       "y": 1
 *     }
 *   }}</pre>
 * This class addresses this problem by adding type information to the
 * serialized JSON and honoring that type information when the JSON is
 * deserialized: <pre>   {@code
 *   {
 *     "bottomShape": {
 *       "type": "Diamond",
 *       "width": 10,
 *       "height": 5,
 *       "x": 0,
 *       "y": 0
 *     },
 *     "topShape": {
 *       "type": "Circle",
 *       "radius": 2,
 *       "x": 4,
 *       "y": 1
 *     }
 *   }}</pre>
 * Both the type field name ({@code "type"}) and the type labels ({@code
 * "Rectangle"}) are configurable.
 *
 * <h3>Registering Types</h3>
 * Create an {@code InnerClassTypeAdapterFactory} by passing the base type and type field
 * name to the {@link #of} factory method. If you don't supply an explicit type
 * field name, {@code "type"} will be used. <pre>   {@code
 *   InnerClassTypeAdapterFactory<Shape> shapeAdapterFactory
 *       = InnerClassTypeAdapterFactory.of(Shape.class, "type");
 * }</pre>
 * Finally, register the type adapter factory in your application's GSON builder:
 * <pre>   {@code
 *   Gson gson = new GsonBuilder()
 *       .registerTypeAdapterFactory(shapeAdapterFactory)
 *       .create();
 * }</pre>
 */
public final class InnerClassTypeAdapterFactory<T> implements TypeAdapterFactory {
  private final Class<?> baseType;
  private final String typeFieldName;

  private InnerClassTypeAdapterFactory(Class<?> baseType, String typeFieldName) {
    if (typeFieldName == null || baseType == null) {
      throw new NullPointerException();
    }
    this.baseType = baseType;
    this.typeFieldName = typeFieldName;
  }

  /**
   * Creates a new runtime type adapter using for {@code baseType} using {@code
   * typeFieldName} as the type field name. Type field names are case sensitive.
   */
  public static <T> InnerClassTypeAdapterFactory<T> of(Class<T> baseType, String typeFieldName) {
    return new InnerClassTypeAdapterFactory<T>(baseType, typeFieldName);
  }

  @Override
  public <R> TypeAdapter<R> create(Gson gson, TypeToken<R> type) {
    if (type.getRawType() != baseType) {
      return null;
    }

    return new TypeAdapter<R>() {
      @Override public R read(JsonReader in) throws IOException {
        JsonElement jsonElement = Streams.parse(in);
        JsonElement labelJsonElement = jsonElement.getAsJsonObject().remove(typeFieldName);
        if (labelJsonElement == null) {
          throw new JsonParseException("cannot deserialize " + baseType
              + " because it does not define a field named " + typeFieldName);
        }
        String label = labelJsonElement.getAsString();
        
        try {
          String subclassName = baseType.getName() + "$" + label.replaceAll("\\s", "");
          Class<?> subclass = Class.forName(subclassName);
          @SuppressWarnings("unchecked")
          TypeAdapter<R> delegate = (TypeAdapter<R>) gson.getDelegateAdapter(
              InnerClassTypeAdapterFactory.this, TypeToken.get(subclass));
          if (delegate == null) {
            throw new JsonParseException("cannot deserialize " + baseType + " subtype named "
                + label);
          }
          return delegate.fromJsonTree(jsonElement);
        } catch (ClassNotFoundException e) {
          throw new JsonParseException("cannot deserialize " + baseType + " subtype named "
              + label);
        }
      }

      @Override public void write(JsonWriter out, R value) throws IOException {
        throw new NotImplementedException("Write not implemented for InnerClassTypeAdapter");
      }
    }.nullSafe();
  }
}
