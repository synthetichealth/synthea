package org.mitre.synthea.helpers;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;

/**
 * The SerializableTypeAdapter is a GSON TypeAdapter used to allow GSON to handle classes
 * that are Serializable, but can't be instrumented by GSON for whatever reason.
 * This is primarily used for core Java classes (java.*.*) which as of JDK16 no longer
 * allow access via reflection. See: https://github.com/google/gson/issues/1216
 *
 * <p>The internal approach is to write the object to a string using ObjectOutputStream,
 * then base64 it. Reading the object reverses the base64 then turns it back into
 * an Object via ObjectInputStream.
 *
 * <p>This class is based on StackOverflow answer https://stackoverflow.com/a/33097652
 *
 * @param <E> Serializable class that cannot be natively deserialized by Gson.
 */
public class SerializableTypeAdapter<E extends Serializable> extends TypeAdapter<E> {

  @Override
  public void write(JsonWriter out, E value) throws IOException {
    // TODO: what if value is null?

    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    ObjectOutputStream so = new ObjectOutputStream(bo);
    so.writeObject(value);
    so.flush();

    String valueString = new String(Base64.getEncoder().encode(bo.toByteArray()));
    out.value(valueString);
  }

  @Override
  public E read(JsonReader in) throws IOException {
    String valueString = in.nextString();
    byte[] b = Base64.getDecoder().decode(valueString.getBytes());
    ByteArrayInputStream bi = new ByteArrayInputStream(b);
    ObjectInputStream si = new ObjectInputStream(bi);
    try {
      @SuppressWarnings("unchecked")
      E value = (E) si.readObject();
      return value;
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  }
}