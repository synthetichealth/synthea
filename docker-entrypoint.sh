#!/usr/bin/env sh

set -eu

output_dir="${SYNTHEA_OUTPUT_DIR:-/synthea-output}"
output_format="${SYNTHEA_OUTPUT_FORMAT:-}"
population="${SYNTHEA_POPULATION:-}"
state="${SYNTHEA_STATE:-}"
city="${SYNTHEA_CITY:-}"
seed="${SYNTHEA_SEED:-}"
clinician_seed="${SYNTHEA_CLINICIAN_SEED:-}"
single_person_seed="${SYNTHEA_SINGLE_PERSON_SEED:-}"
reference_date="${SYNTHEA_REFERENCE_DATE:-}"
end_date="${SYNTHEA_END_DATE:-}"
gender="${SYNTHEA_GENDER:-}"
age_range="${SYNTHEA_AGE_RANGE:-}"
overflow="${SYNTHEA_OVERFLOW:-}"
modules="${SYNTHEA_MODULES:-}"
config_file="${SYNTHEA_CONFIG_FILE:-}"
local_modules_dir="${SYNTHEA_LOCAL_MODULES_DIR:-}"
initial_snapshot="${SYNTHEA_INITIAL_SNAPSHOT:-}"
updated_snapshot="${SYNTHEA_UPDATED_SNAPSHOT:-}"
update_days="${SYNTHEA_UPDATE_DAYS:-}"
fixed_record_path="${SYNTHEA_FIXED_RECORD_PATH:-}"
keep_patients_path="${SYNTHEA_KEEP_PATIENTS_PATH:-}"

mkdir -p "$output_dir"

original_argc=$#

if [ "$original_argc" -eq 1 ] && [ "$1" = "--help-env" ]; then
	echo "Supported runtime environment variables:" >&2
	echo "  SYNTHEA_OUTPUT_DIR" >&2
	echo "  SYNTHEA_OUTPUT_FORMAT" >&2
	echo "  SYNTHEA_POPULATION" >&2
	echo "  SYNTHEA_STATE" >&2
	echo "  SYNTHEA_CITY" >&2
	echo "  SYNTHEA_SEED" >&2
	echo "  SYNTHEA_CLINICIAN_SEED" >&2
	echo "  SYNTHEA_SINGLE_PERSON_SEED" >&2
	echo "  SYNTHEA_REFERENCE_DATE" >&2
	echo "  SYNTHEA_END_DATE" >&2
	echo "  SYNTHEA_GENDER" >&2
	echo "  SYNTHEA_AGE_RANGE" >&2
	echo "  SYNTHEA_OVERFLOW" >&2
	echo "  SYNTHEA_MODULES" >&2
	echo "  SYNTHEA_CONFIG_FILE" >&2
	echo "  SYNTHEA_LOCAL_MODULES_DIR" >&2
	echo "  SYNTHEA_INITIAL_SNAPSHOT" >&2
	echo "  SYNTHEA_UPDATED_SNAPSHOT" >&2
	echo "  SYNTHEA_UPDATE_DAYS" >&2
	echo "  SYNTHEA_FIXED_RECORD_PATH" >&2
	echo "  SYNTHEA_KEEP_PATIENTS_PATH" >&2
	exit 0
fi

if [ "$original_argc" -gt 0 ]; then
	echo "Runtime CLI arguments are not supported in this image." >&2
	echo "Configure Synthea using SYNTHEA_* environment variables instead." >&2
	echo "Use --help-env to list supported environment variables." >&2
	exit 1
fi

set -- "--exporter.baseDirectory=$output_dir" "$@"

if [ -n "$output_format" ]; then
	for config_key in \
		exporter.ccda.export \
		exporter.fhir.export \
		exporter.fhir_stu3.export \
		exporter.fhir_dstu2.export \
		exporter.json.export \
		exporter.csv.export \
		exporter.cpcds.export \
		exporter.bfd.export \
		exporter.cdw.export \
		exporter.text.export \
		exporter.clinical_note.export \
		exporter.hospital.fhir.export \
		exporter.hospital.fhir_stu3.export \
		exporter.hospital.fhir_dstu2.export \
		exporter.practitioner.fhir.export \
		exporter.practitioner.fhir_stu3.export \
		exporter.practitioner.fhir_dstu2.export
	do
		set -- "$@" "--$config_key=false"
	done

	set -- "$@" "--exporter.fhir.bulk_data=false"

	old_ifs="$IFS"
	IFS=,
	for raw_format in $output_format; do
		IFS="$old_ifs"
		format=$(printf '%s' "$raw_format" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')

		case "$format" in
			fhir)
				set -- "$@" \
					--exporter.fhir.export=true \
					--exporter.hospital.fhir.export=true \
					--exporter.practitioner.fhir.export=true
				;;
			bulk_fhir|bulk-fhir)
				set -- "$@" \
					--exporter.fhir.export=true \
					--exporter.fhir.bulk_data=true \
					--exporter.hospital.fhir.export=true \
					--exporter.practitioner.fhir.export=true
				;;
			fhir_stu3|stu3)
				set -- "$@" \
					--exporter.fhir_stu3.export=true \
					--exporter.hospital.fhir_stu3.export=true \
					--exporter.practitioner.fhir_stu3.export=true
				;;
			fhir_dstu2|dstu2)
				set -- "$@" \
					--exporter.fhir_dstu2.export=true \
					--exporter.hospital.fhir_dstu2.export=true \
					--exporter.practitioner.fhir_dstu2.export=true
				;;
			ccda)
				set -- "$@" --exporter.ccda.export=true
				;;
			json)
				set -- "$@" --exporter.json.export=true
				;;
			csv)
				set -- "$@" --exporter.csv.export=true
				;;
			cpcds)
				set -- "$@" --exporter.cpcds.export=true
				;;
			bfd)
				set -- "$@" --exporter.bfd.export=true
				;;
			cdw)
				set -- "$@" --exporter.cdw.export=true
				;;
			text)
				set -- "$@" --exporter.text.export=true
				;;
			clinical_note|clinical-note)
				set -- "$@" --exporter.clinical_note.export=true
				;;
			'')
				;;
			*)
				echo "Unsupported SYNTHEA_OUTPUT_FORMAT value: $raw_format" >&2
				echo "Supported values: fhir, bulk_fhir, fhir_stu3, fhir_dstu2, ccda, json, csv, cpcds, bfd, cdw, text, clinical_note" >&2
				exit 1
				;;
		esac

		IFS=,
	done
	IFS="$old_ifs"
fi

[ -n "$seed" ] && set -- "$@" -s "$seed"
[ -n "$clinician_seed" ] && set -- "$@" -cs "$clinician_seed"
[ -n "$single_person_seed" ] && set -- "$@" -ps "$single_person_seed"
[ -n "$population" ] && set -- "$@" -p "$population"
[ -n "$reference_date" ] && set -- "$@" -r "$reference_date"
[ -n "$end_date" ] && set -- "$@" -e "$end_date"
[ -n "$gender" ] && set -- "$@" -g "$gender"
[ -n "$age_range" ] && set -- "$@" -a "$age_range"
[ -n "$overflow" ] && set -- "$@" -o "$overflow"
[ -n "$modules" ] && set -- "$@" -m "$modules"
[ -n "$config_file" ] && set -- "$@" -c "$config_file"
[ -n "$local_modules_dir" ] && set -- "$@" -d "$local_modules_dir"
[ -n "$initial_snapshot" ] && set -- "$@" -i "$initial_snapshot"
[ -n "$updated_snapshot" ] && set -- "$@" -u "$updated_snapshot"
[ -n "$update_days" ] && set -- "$@" -t "$update_days"
[ -n "$fixed_record_path" ] && set -- "$@" -f "$fixed_record_path"
[ -n "$keep_patients_path" ] && set -- "$@" -k "$keep_patients_path"
[ -n "$state" ] && set -- "$@" "$state"
[ -n "$city" ] && set -- "$@" "$city"

exec java ${JAVA_OPTS:-} -jar /app/synthea.jar "$@"
