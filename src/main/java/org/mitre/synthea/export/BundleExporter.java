package org.mitre.synthea.export;

import org.hl7.fhir.instance.model.api.IBaseBundle;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class BundleExporter {

	private static List<BiConsumer<IBaseBundle, Exporter.SupportedFhirVersion>> consumers = new CopyOnWriteArrayList<>();

	public static Registration addConsumer(BiConsumer<IBaseBundle, Exporter.SupportedFhirVersion> consumer) {
		consumers.add(consumer);
		return () -> consumers.remove(consumer);
	}

	public static Registration setConsumer(BiConsumer<IBaseBundle, Exporter.SupportedFhirVersion> consumer) {
		consumers = new CopyOnWriteArrayList<>();
		consumers.add(consumer);
		return () -> consumers.remove(consumer);
	}

	public static void export(IBaseBundle fhirBundle, Exporter.SupportedFhirVersion fhirVersion) {
		for (BiConsumer<IBaseBundle, Exporter.SupportedFhirVersion> consumer : consumers) {
			consumer.accept(fhirBundle, fhirVersion);
		}
	}

	@FunctionalInterface
	public interface Registration extends Serializable {
		void remove();
	}
}
